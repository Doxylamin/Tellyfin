package app.tellyfin.androidtv.data.prefs

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tellyfin.androidtv.diagnostics.CrashReporting
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

// One-time migration off the pre-rebrand "jellytv_prefs" file so existing installs keep
// their saved server/login/favourites instead of being silently logged out on update.
private class LegacyPrefsMigration(private val context: Context) : DataMigration<Preferences> {
    private fun legacyFile() = File(context.filesDir, "datastore/jellytv_prefs.preferences_pb")

    // Only set once migrate() has actually read the legacy file, so cleanUp() never deletes it
    // out from under a timed-out attempt — that data has to survive for a later retry.
    private var migrated = false

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().isEmpty() && legacyFile().exists()

    override suspend fun migrate(currentData: Preferences): Preferences {
        CrashReporting.addBreadcrumb("LegacyPrefsMigration.migrate() starting", "startup")
        // A process from an older install can still be resident and holding this file's lock
        // (e.g. after a sideload update without a force-stop) — that must never block the whole
        // app's startup waiting on a one-time convenience migration.
        val legacyData = withTimeoutOrNull(LEGACY_MIGRATION_TIMEOUT_MS) {
            val legacyStore = PreferenceDataStoreFactory.create(produceFile = ::legacyFile)
            legacyStore.data.first()
        }
        migrated = legacyData != null
        CrashReporting.addBreadcrumb(
            if (migrated) "LegacyPrefsMigration read legacy data" else "LegacyPrefsMigration timed out / no legacy data",
            "startup"
        )
        return legacyData ?: currentData
    }

    override suspend fun cleanUp() {
        if (migrated) legacyFile().delete()
    }
}

private const val LEGACY_MIGRATION_TIMEOUT_MS = 2_000L

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tellyfin_prefs",
    produceMigrations = { context -> listOf(LegacyPrefsMigration(context)) }
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val LAST_CHANNEL_INDEX = intPreferencesKey("last_channel_index")
        val MAX_BITRATE = intPreferencesKey("max_bitrate")
        val FAVORITE_IDS = stringPreferencesKey("favorite_ids")
        val PREBUFFER_ENABLED = booleanPreferencesKey("prebuffer_enabled")
        val PREBUFFER_AUTO_DISABLED = booleanPreferencesKey("prebuffer_auto_disabled")
        val KEYBINDS = stringPreferencesKey("keybinds")
        val PREBUFFER_DELAY_MS = longPreferencesKey("prebuffer_delay_ms")
        val COUNTDOWN_MS = longPreferencesKey("countdown_ms")
        val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val username: Flow<String> = context.dataStore.data.map { it[Keys.USERNAME] ?: "" }
    val lastChannelIndex: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_CHANNEL_INDEX] ?: 0 }
    val maxBitrate: Flow<Int?> = context.dataStore.data.map { it[Keys.MAX_BITRATE] }
    val prebufferEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PREBUFFER_ENABLED] ?: true }
    val prebufferAutoDisabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PREBUFFER_AUTO_DISABLED] ?: false }
    val keybinds: Flow<String?> = context.dataStore.data.map { it[Keys.KEYBINDS] }
    val prebufferDelayMs: Flow<Long?> = context.dataStore.data.map { it[Keys.PREBUFFER_DELAY_MS] }
    val countdownMs: Flow<Long?> = context.dataStore.data.map { it[Keys.COUNTDOWN_MS] }
    val diagnosticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DIAGNOSTICS_ENABLED] ?: false }
    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.FAVORITE_IDS]?.split(",")?.filter { s -> s.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun saveSession(serverUrl: String, accessToken: String, userId: String, username: String = "") {
        CrashReporting.addBreadcrumb("PreferencesRepository.saveSession() calling dataStore.edit()", "prefs")
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl.trimEnd('/')
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.USER_ID] = userId
            if (username.isNotBlank()) prefs[Keys.USERNAME] = username
        }
        CrashReporting.addBreadcrumb("PreferencesRepository.saveSession() dataStore.edit() returned", "prefs")
    }

    suspend fun saveLastChannelIndex(index: Int) {
        context.dataStore.edit { it[Keys.LAST_CHANNEL_INDEX] = index }
    }

    suspend fun saveMaxBitrate(bitrate: Int?) {
        context.dataStore.edit {
            if (bitrate == null) it.remove(Keys.MAX_BITRATE)
            else it[Keys.MAX_BITRATE] = bitrate
        }
    }

    /** [autoDisabled] records that the app turned it off itself, so Settings can say why. */
    suspend fun savePrebuffer(enabled: Boolean, autoDisabled: Boolean) {
        context.dataStore.edit {
            it[Keys.PREBUFFER_ENABLED] = enabled
            it[Keys.PREBUFFER_AUTO_DISABLED] = autoDisabled
        }
    }

    suspend fun saveKeybinds(serialized: String) {
        context.dataStore.edit { it[Keys.KEYBINDS] = serialized }
    }

    suspend fun savePrebufferDelayMs(ms: Long) {
        context.dataStore.edit { it[Keys.PREBUFFER_DELAY_MS] = ms }
    }

    suspend fun saveCountdownMs(ms: Long) {
        context.dataStore.edit { it[Keys.COUNTDOWN_MS] = ms }
    }

    suspend fun saveDiagnosticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIAGNOSTICS_ENABLED] = enabled }
    }

    /** Settings → Advanced → Restore defaults: that page's settings plus every added button. */
    suspend fun clearAdvancedSettings() {
        context.dataStore.edit {
            it.remove(Keys.KEYBINDS)
            it.remove(Keys.PREBUFFER_DELAY_MS)
            it.remove(Keys.COUNTDOWN_MS)
            it.remove(Keys.DIAGNOSTICS_ENABLED)
        }
    }

    suspend fun saveFavoriteIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.FAVORITE_IDS] = ids.joinToString(",") }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
