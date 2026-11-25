package jp.tsuzutore.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import jp.tsuzutore.app.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.util.UUID

private val Context.tsuzutorePreferences by preferencesDataStore(name = "tsuzutore_settings")

@Serializable
private data class ImportSettingsJournal(val token: String, val previous: AppSettings, val replacement: AppSettings)

/** One repository per Application. All state-changing operations are serialized and idempotent by ID. */
class AppRepository internal constructor(private val db: AppDatabase, private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tsuzutore.db").build(),
        context.applicationContext.tsuzutorePreferences,
    )
    private val dao = db.dao()
    private val json = BackupCodec.json
    private val writeMutex = Mutex()
    private val initializationMutex = Mutex()
    @Volatile private var initialized = false
    private val settingsKey = stringPreferencesKey("settings_json_v1")
    private val importJournalKey = stringPreferencesKey("import_settings_journal_v1")

    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val templates: Flow<List<MenuTemplate>> = flow {
        ensureInitialized()
        emitAll(dao.observeTemplates().map { rows -> rows.map { it.toModel() } })
    }
    val sessions: Flow<List<WorkoutSession>> = flow {
        ensureInitialized()
        emitAll(dao.observeSessions().map { rows -> rows.map { it.toModel() } })
    }
    val settings: Flow<AppSettings> = flow {
        ensureInitialized()
        emitAll(preferences.map { readSettings(it) }.distinctUntilChanged())
    }

    fun observeTemplates(): Flow<List<MenuTemplate>> = templates
    fun observeSessions(): Flow<List<WorkoutSession>> = sessions

    private suspend fun ensureInitialized() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return@withLock
            writeMutex.withLock {
                // Resolve a process death between DataStore and Room commits deterministically.
                val prefs = preferences.first()
                prefs[importJournalKey]?.let { encoded ->
                    val journal = json.decodeFromString<ImportSettingsJournal>(encoded)
                    val committed = dao.metadata("last_import_token")?.value == journal.token
                    store.edit {
                        it[settingsKey] = json.encodeToString(if (committed) journal.replacement else journal.previous)
                        it.remove(importJournalKey)
                    }
                }
                db.withTransaction {
                    if (dao.metadata("initial_menu_seeded") == null) {
                        writeTemplate(initialTemplate(), 0)
                        dao.upsertMetadata(MetadataEntity("initial_menu_seeded", "true"))
                    }
                }
            }
            initialized = true
        }
    }

    suspend fun saveTemplate(template: MenuTemplate) {
        validateTemplate(template).firstOrNull()?.let { throw IllegalArgumentException(it) }
        ensureInitialized()
        writeMutex.withLock {
            db.withTransaction { writeTemplate(template, dao.template(template.id)?.position ?: dao.nextTemplatePosition()) }
        }
    }

    suspend fun deleteTemplate(id: String) {
        ensureInitialized()
        writeMutex.withLock { db.withTransaction { dao.deleteTemplate(id) } }
    }

    suspend fun reorderTemplates(ids: List<String>) {
        ensureInitialized()
        writeMutex.withLock {
            db.withTransaction {
                val rows = dao.allTemplates()
                require(ids.size == ids.toSet().size && ids.toSet() == rows.map { it.template.id }.toSet()) { "並べ替えるメニューが一致していません。" }
                val byId = rows.associateBy { it.template.id }
                ids.forEachIndexed { index, id -> dao.upsertTemplate(byId.getValue(id).template.copy(position = index)) }
            }
        }
    }

    /** A session, its set results and its runtime snapshot always commit together. */
    suspend fun saveRun(state: RunState) {
        ensureInitialized()
        writeMutex.withLock {
            db.withTransaction {
                val previous = dao.runtime()?.let { json.decodeFromString<RunState>(it.payload) }
                if (previous != null && previous.session?.id == state.session?.id && state.revision < previous.revision) return@withTransaction
                state.session?.let { writeSession(it) }
                dao.upsertRuntime(RuntimeEntity(payload = json.encodeToString(state)))
            }
        }
    }

    suspend fun saveSession(session: WorkoutSession) {
        validateSession(session).firstOrNull()?.let { throw IllegalArgumentException(it) }
        ensureInitialized()
        writeMutex.withLock {
            db.withTransaction {
                writeSession(session)
                val saved = dao.runtime()?.let { json.decodeFromString<RunState>(it.payload) }
                if (saved?.session?.id == session.id) {
                    dao.upsertRuntime(RuntimeEntity(payload = json.encodeToString(saved.copy(session = session))))
                }
            }
        }
    }

    /** Loading never starts time: even a previously running interval waits for explicit Resume. */
    suspend fun loadRun(): RunState? {
        ensureInitialized()
        return writeMutex.withLock {
            dao.runtime()?.let { restorePaused(json.decodeFromString<RunState>(it.payload)) }
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        ensureInitialized()
        writeMutex.withLock {
            store.edit { prefs ->
                val replacement = transform(readSettings(prefs))
                validateSettings(replacement).firstOrNull()?.let { throw IllegalArgumentException(it) }
                prefs[settingsKey] = json.encodeToString(replacement)
            }
        }
    }

    suspend fun exportBackup(): String {
        ensureInitialized()
        return writeMutex.withLock {
            val document = db.withTransaction {
                BackupDocument(BackupCodec.SCHEMA_VERSION, readSettings(preferences.first()),
                    dao.allTemplates().map { it.toModel() }, dao.allSessions().map { it.toModel() },
                    dao.runtime()?.let { json.decodeFromString<RunState>(it.payload) })
            }
            BackupCodec.encode(document)
        }
    }

    fun previewImport(text: String): BackupPreview = BackupCodec.preview(BackupCodec.decode(text))

    /** Caller presents preview + confirmation first. Invalid input never touches either store. */
    suspend fun importBackup(text: String) {
        val decoded = BackupCodec.decode(text)
        val replacement = BackupCodec.forRestore(decoded)
        ensureInitialized()
        writeMutex.withLock {
            val oldSettings = readSettings(preferences.first())
            val token = UUID.randomUUID().toString()
            val journal = ImportSettingsJournal(token, oldSettings, replacement.settings)
            store.edit { it[importJournalKey] = json.encodeToString(journal) }
            try {
                db.withTransaction {
                    dao.deleteRuntime()
                    dao.deleteAllResults()
                    dao.deleteAllSessions()
                    dao.deleteAllExercises()
                    dao.deleteAllTemplates()
                    replacement.templates.forEachIndexed { index, template -> writeTemplate(template, index) }
                    replacement.sessions.forEach { writeSession(it) }
                    replacement.runtime?.let { dao.upsertRuntime(RuntimeEntity(payload = json.encodeToString(it))) }
                    dao.upsertMetadata(MetadataEntity("initial_menu_seeded", "true"))
                    dao.upsertMetadata(MetadataEntity("last_import_token", token))
                    store.edit { it[settingsKey] = json.encodeToString(replacement.settings) }
                }
            } catch (error: Throwable) {
                // Cancellation cannot interrupt rollback. If commit completed just before cancellation,
                // retain replacement settings; otherwise Room rolled back and old settings must win.
                withContext(NonCancellable) {
                    runCatching {
                        val committed = dao.metadata("last_import_token")?.value == token
                        store.edit {
                            it[settingsKey] = json.encodeToString(if (committed) replacement.settings else oldSettings)
                            it.remove(importJournalKey)
                        }
                    }
                }
                throw error
            }
            // A failed cleanup is harmless: the committed token resolves it on the next launch.
            runCatching { store.edit { it.remove(importJournalKey) } }
        }
    }

    private fun readSettings(prefs: Preferences): AppSettings = prefs[settingsKey]
        ?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()

    private suspend fun writeTemplate(template: MenuTemplate, position: Int) {
        dao.upsertTemplate(TemplateEntity(template.id, template.name, position))
        dao.deleteExercises(template.id)
        dao.insertExercises(template.exercises.mapIndexed { index, exercise ->
            ExerciseEntity(template.id, exercise.id, index, json.encodeToString(exercise))
        })
    }

    private suspend fun writeSession(session: WorkoutSession) {
        dao.upsertSession(SessionEntity(session.id, session.startedAt, session.localDate,
            json.encodeToString(session.copy(results = emptyList()))))
        dao.deleteResults(session.id)
        dao.insertResults(session.results.mapIndexed { index, result ->
            ResultEntity(result.id, session.id, index, json.encodeToString(result))
        })
    }

    private fun TemplateWithExercises.toModel() = MenuTemplate(template.id, template.name,
        exercises.sortedBy { it.position }.map { json.decodeFromString<Exercise>(it.payload) })
    private fun SessionWithResults.toModel() = json.decodeFromString<WorkoutSession>(session.payload)
        .copy(results = results.sortedBy { it.position }.map { json.decodeFromString<SetResult>(it.payload) })
}
