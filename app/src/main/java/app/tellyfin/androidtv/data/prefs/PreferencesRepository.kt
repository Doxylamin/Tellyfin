package app.tellyfin.androidtv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jellytv_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val LAST_CHANNEL_INDEX = intPreferencesKey("last_channel_index")
        val MAX_BITRATE = intPreferencesKey("max_bitrate")
        val FAVORITE_IDS = stringPreferencesKey("favorite_ids")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val username: Flow<String> = context.dataStore.data.map { it[Keys.USERNAME] ?: "" }
    val lastChannelIndex: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_CHANNEL_INDEX] ?: 0 }
    val maxBitrate: Flow<Int?> = context.dataStore.data.map { it[Keys.MAX_BITRATE] }
    val favoriteIds: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.FAVORITE_IDS]?.split(",")?.filter { s -> s.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun saveSession(serverUrl: String, accessToken: String, userId: String, username: String = "") {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl.trimEnd('/')
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.USER_ID] = userId
            if (username.isNotBlank()) prefs[Keys.USERNAME] = username
        }
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

    suspend fun saveFavoriteIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.FAVORITE_IDS] = ids.joinToString(",") }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
