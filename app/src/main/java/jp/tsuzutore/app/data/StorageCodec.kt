package jp.tsuzutore.app.data

import kotlinx.serialization.json.Json

/**
 * Encoding for the app's own Room/DataStore payloads.
 *
 * Unlike backup import, internal storage must survive app updates and reinstalling an older APK:
 * a field added or removed by another version is ignored instead of making every launch fail.
 * Payloads are compact; older pretty-printed payloads still decode.
 */
internal object StorageCodec {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}
