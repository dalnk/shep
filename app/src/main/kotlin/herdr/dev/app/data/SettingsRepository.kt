package herdr.dev.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SERVER_HOST_KEY = stringPreferencesKey("server_host")
private val SERVER_PORT_KEY = stringPreferencesKey("server_port")
private val IROH_NODE_ID_KEY = stringPreferencesKey("iroh_node_id")
private val CONNECTION_TYPE_KEY = stringPreferencesKey("connection_type")
private val DANGER_LEVEL_KEY = stringPreferencesKey("danger_level")
private val ONBOARDING_COMPLETED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")
private val ACCENT_COLOR_INDEX_KEY = androidx.datastore.preferences.core.intPreferencesKey("accent_color_index")
private val LIVE_UPDATES_RENDER_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("live_updates_render")

enum class ConnectionType {
    LOCAL, IROH
}

enum class DangerLevel(val displayName: String, val description: String) {
    ZERO_DANGER("0 Danger", "Zero autonomous risk. Never auto-proceed; explicit manual confirmation required."),
    NORMAL("Normal", "Standard supervision. Requires manual approval when an agent pauses or requests permission."),
    DANGERMAXXING("Dangermaxxing", "Maximum autonomy. Automatically proceeds and approves paused agents so the swarm never stops.")
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val accentColorIndex: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR_INDEX_KEY] ?: 0
    }

    val liveUpdatesRender: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LIVE_UPDATES_RENDER_KEY] ?: true
    }
    val dangerLevel: Flow<DangerLevel> = context.dataStore.data.map { preferences ->
        try {
            DangerLevel.valueOf(preferences[DANGER_LEVEL_KEY] ?: DangerLevel.NORMAL.name)
        } catch (_: Exception) {
            DangerLevel.NORMAL
        }
    }
    val serverHost: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_HOST_KEY] ?: "neo.local"
    }

    val serverPort: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_PORT_KEY] ?: "8765"
    }

    val irohNodeId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[IROH_NODE_ID_KEY] ?: ""
    }

    val connectionType: Flow<ConnectionType> = context.dataStore.data.map { preferences ->
        ConnectionType.valueOf(preferences[CONNECTION_TYPE_KEY] ?: ConnectionType.LOCAL.name)
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setServerHost(host: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_HOST_KEY] = host
        }
    }

    suspend fun setServerPort(port: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_PORT_KEY] = port
        }
    }

    suspend fun setIrohNodeId(nodeId: String) {
        context.dataStore.edit { preferences ->
            preferences[IROH_NODE_ID_KEY] = nodeId
        }
    }

    suspend fun setConnectionType(type: ConnectionType) {
        context.dataStore.edit { preferences ->
            preferences[CONNECTION_TYPE_KEY] = type.name
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun setDangerLevel(level: DangerLevel) {
        context.dataStore.edit { preferences ->
            preferences[DANGER_LEVEL_KEY] = level.name
        }
    }

    suspend fun setAccentColorIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_INDEX_KEY] = index
        }
    }

    suspend fun setLiveUpdatesRender(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LIVE_UPDATES_RENDER_KEY] = enabled
        }
    }

    val cacheDir: java.io.File
        get() = context.cacheDir
}
