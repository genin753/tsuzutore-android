package jp.tsuzutore.app.data

import jp.tsuzutore.app.domain.AppSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class StorageCodecTest {
    @Test fun settingsStoredByAnotherAppVersionStillLoad() {
        val fromNewerVersion = """{"plannedDays":[1,3],"weeklyGoal":4,"someFutureSetting":true}"""
        val settings = StorageCodec.json.decodeFromString<AppSettings>(fromNewerVersion)
        assertEquals(setOf(1, 3), settings.plannedDays)
        assertEquals(4, settings.weeklyGoal)
        assertFalse(settings.cuesInSilentMode)
    }

    @Test fun compactPayloadsRoundTripAndEarlierPrettyPayloadsStillDecode() {
        val settings = AppSettings(weeklyGoal = 5, cueMemo = "朝食のあと", cuesInSilentMode = true)
        val compact = StorageCodec.json.encodeToString(settings)
        assertFalse(compact.contains('\n'))
        assertEquals(settings, StorageCodec.json.decodeFromString<AppSettings>(compact))
        val earlier = BackupCodec.json.encodeToString(settings)
        assertEquals(settings, StorageCodec.json.decodeFromString<AppSettings>(earlier))
    }

    @Test fun backupImportStaysStrictWhileStorageIsTolerant() {
        val withUnknownField = """{"schemaVersion":1,"settings":{},"templates":[],"sessions":[],"unexpected":1}"""
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(withUnknownField) }
    }
}
