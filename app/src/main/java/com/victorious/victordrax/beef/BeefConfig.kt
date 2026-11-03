package com.victorious.victordrax.beef

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.victorious.victordrax.data.Scope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent BeEF connection settings, kept in their OWN DataStore file (separate from the
 * app's main settings). Host, port and username/password are stored here so the operator does
 * not retype them. The REST *token* is NEVER persisted — it lives only in memory on [BeefClient].
 *
 * No credentials are ever committed to the repository; they are entered by the user at runtime.
 */
private val Context.beefDataStore by preferencesDataStore(name = "beef_settings")

data class BeefConfig(
    val host: String = BeefApi.DEFAULT_HOST,
    val port: Int = BeefApi.DEFAULT_PORT,
    val username: String = "beef",
    val password: String = "",
    /** Absolute path of the BeEF install dir inside the VictorSuite/ANDRAX environment. */
    val installDir: String = DEFAULT_INSTALL_DIR,
) {
    val baseUrl: String get() = BeefApi.baseUrl(host, port)
    val isValid: Boolean get() = isValidHost(host) && isValidPort(port)

    companion object {
        const val DEFAULT_INSTALL_DIR = "/data/local/andrax/beef"

        /** A host is valid if it is a hostname or an IPv4 literal (BeEF usually runs on 127.0.0.1). */
        fun isValidHost(h: String): Boolean {
            val t = h.trim()
            return t.isNotBlank() && (Scope.isIpv4(t) || Scope.isHostname(t) || t == "localhost")
        }
        fun isValidPort(p: Int): Boolean = p in 1..65535
    }
}

class BeefConfigRepo(private val ctx: Context) {
    private object K {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val user = stringPreferencesKey("username")
        val pass = stringPreferencesKey("password")
        val dir = stringPreferencesKey("install_dir")
    }

    val config: Flow<BeefConfig> = ctx.beefDataStore.data.map { p ->
        BeefConfig(
            host = p[K.host] ?: BeefApi.DEFAULT_HOST,
            port = p[K.port] ?: BeefApi.DEFAULT_PORT,
            username = p[K.user] ?: "beef",
            password = p[K.pass] ?: "",
            installDir = p[K.dir] ?: BeefConfig.DEFAULT_INSTALL_DIR,
        )
    }

    suspend fun current(): BeefConfig = config.first()

    suspend fun save(c: BeefConfig) = ctx.beefDataStore.edit {
        it[K.host] = c.host.trim()
        it[K.port] = c.port
        it[K.user] = c.username.trim()
        it[K.pass] = c.password
        it[K.dir] = c.installDir.trim()
    }
}
