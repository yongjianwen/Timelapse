package xiong.jianwen.timelapse.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferences(private val context: Context) {

    companion object {
        private const val USER_PREFERENCES = "user_preferences"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
            USER_PREFERENCES
        )
        private val INTERVAL = intPreferencesKey("interval")
        private val DURATION = intPreferencesKey("duration")
        private val IS_MUTED = booleanPreferencesKey("is_muted")
    }

    suspend fun saveUserPreferences(interval: Int, duration: Int, isMuted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INTERVAL] = interval
            preferences[DURATION] = duration
            preferences[IS_MUTED] = isMuted
        }
    }

    suspend fun saveDuration(duration: Int) {
        context.dataStore.edit { preferences -> preferences[DURATION] = duration }
    }

    suspend fun saveIsMuted(isMuted: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_MUTED] = isMuted }
    }

    val intervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[INTERVAL] ?: Constants.DEFAULT_INTERVAL
    }

    val durationFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DURATION] ?: Constants.DEFAULT_DURATION
    }

    val isMutedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_MUTED] ?: Constants.DEFAULT_IS_MUTED
    }
}