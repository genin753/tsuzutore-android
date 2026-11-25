package jp.tsuzutore.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "menu_templates")
data class TemplateEntity(@PrimaryKey val id: String, val name: String, val position: Int)

@Entity(
    tableName = "exercise_definitions", primaryKeys = ["templateId", "id"],
    foreignKeys = [ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateId")],
)
data class ExerciseEntity(val templateId: String, val id: String, val position: Int, val payload: String)

data class TemplateWithExercises(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId") val exercises: List<ExerciseEntity>,
)

/** Session payload is an immutable plan snapshot, independent of all template tables. */
@Entity(tableName = "workout_sessions")
data class SessionEntity(@PrimaryKey val id: String, val startedAt: Long, val localDate: String, val payload: String)

@Entity(
    tableName = "set_results",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class ResultEntity(@PrimaryKey val id: String, val sessionId: String, val position: Int, val payload: String)

data class SessionWithResults(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val results: List<ResultEntity>,
)

@Entity(tableName = "runtime_snapshot")
data class RuntimeEntity(@PrimaryKey val id: Int = 1, val payload: String)

@Entity(tableName = "app_metadata")
data class MetadataEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface AppDao {
    @Transaction @Query("SELECT * FROM menu_templates ORDER BY position ASC")
    fun observeTemplates(): Flow<List<TemplateWithExercises>>
    @Transaction @Query("SELECT * FROM menu_templates ORDER BY position ASC")
    suspend fun allTemplates(): List<TemplateWithExercises>
    @Transaction @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC, id ASC")
    fun observeSessions(): Flow<List<SessionWithResults>>
    @Transaction @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC, id ASC")
    suspend fun allSessions(): List<SessionWithResults>
    @Query("SELECT * FROM runtime_snapshot WHERE id = 1") suspend fun runtime(): RuntimeEntity?
    @Query("SELECT * FROM app_metadata WHERE `key` = :key") suspend fun metadata(key: String): MetadataEntity?
    @Query("SELECT * FROM menu_templates WHERE id = :id") suspend fun template(id: String): TemplateEntity?
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM menu_templates") suspend fun nextTemplatePosition(): Int
    @Upsert suspend fun upsertTemplate(entity: TemplateEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(entities: List<ExerciseEntity>)
    @Upsert suspend fun upsertSession(entity: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertResults(entities: List<ResultEntity>)
    @Upsert suspend fun upsertRuntime(entity: RuntimeEntity)
    @Upsert suspend fun upsertMetadata(entity: MetadataEntity)
    @Query("DELETE FROM exercise_definitions WHERE templateId = :templateId") suspend fun deleteExercises(templateId: String)
    @Query("DELETE FROM menu_templates WHERE id = :id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM set_results WHERE sessionId = :sessionId") suspend fun deleteResults(sessionId: String)
    @Query("DELETE FROM runtime_snapshot") suspend fun deleteRuntime()
    @Query("DELETE FROM set_results") suspend fun deleteAllResults()
    @Query("DELETE FROM workout_sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM exercise_definitions") suspend fun deleteAllExercises()
    @Query("DELETE FROM menu_templates") suspend fun deleteAllTemplates()
}

@Database(entities = [TemplateEntity::class, ExerciseEntity::class, SessionEntity::class, ResultEntity::class,
    RuntimeEntity::class, MetadataEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
