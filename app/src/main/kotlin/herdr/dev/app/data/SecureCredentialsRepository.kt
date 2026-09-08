package herdr.dev.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ssh_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _sshCredentials = MutableStateFlow<SSHCredentials?>(null)
    val sshCredentials: Flow<SSHCredentials?> = _sshCredentials.asStateFlow()

    init {
        loadSSHCredentials()
    }

    fun saveSSHCredentials(credentials: SSHCredentials) {
        sharedPreferences.edit().apply {
            putString(KEY_HOST, credentials.host)
            putInt(KEY_PORT, credentials.port)
            putString(KEY_USERNAME, credentials.username)
            putString(KEY_PASSWORD, credentials.password)
            putBoolean(KEY_SAVE_CREDENTIALS, credentials.saveCredentials)
        }.apply()
        
        _sshCredentials.value = credentials
    }

    fun loadSSHCredentials(): SSHCredentials? {
        val credentials = if (sharedPreferences.getBoolean(KEY_SAVE_CREDENTIALS, false)) {
            SSHCredentials(
                host = sharedPreferences.getString(KEY_HOST, "") ?: "",
                port = sharedPreferences.getInt(KEY_PORT, 22),
                username = sharedPreferences.getString(KEY_USERNAME, "") ?: "",
                password = sharedPreferences.getString(KEY_PASSWORD, "") ?: "",
                saveCredentials = true
            )
        } else {
            null
        }
        
        _sshCredentials.value = credentials
        return credentials
    }

    fun clearSSHCredentials() {
        sharedPreferences.edit().clear().apply()
        _sshCredentials.value = null
    }

    data class SSHCredentials(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val saveCredentials: Boolean
    )

    companion object {
        private const val KEY_HOST = "ssh_host"
        private const val KEY_PORT = "ssh_port"
        private const val KEY_USERNAME = "ssh_username"
        private const val KEY_PASSWORD = "ssh_password"
        private const val KEY_SAVE_CREDENTIALS = "ssh_save_credentials"
    }
}
