package com.victorious.victordrax.mitm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent mitmdump settings in their OWN DataStore file (not the app settings / Room).
 * No secrets are stored — listen port, bind host, dump path only.
 */
private val Context.mitmDataStore by preferencesDataStore(name = "mitm_proxy")

data class MitmConfig(
    /** Address the proxy advertises / binds (usually 0.0.0.0 or the device LAN IP). */
    val listenHost: String = DEFAULT_HOST,
    val listenPort: Int = DEFAULT_PORT,
    /** Where mitmdump writes its flow file inside VictorSuite home. */
    val flowPath: String = DEFAULT_FLOW_PATH,
    /** Where a JSONL export is written for the in-app viewer. */
    val jsonlPath: String = DEFAULT_JSONL_PATH,
) {
    val proxyUrl: String get() = "http://$listenHost:$listenPort"
    val isValid: Boolean get() = isValidHost(listenHost) && isValidPort(listenPort)

    companion object {
        const val DEFAULT_HOST = "0.0.0.0"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_FLOW_PATH = "/data/data/com.termux/files/home/victordrax-mitm.flow"
        const val DEFAULT_JSONL_PATH = "/data/data/com.termux/files/home/victordrax-mitm.jsonl"
        const val CA_CERT_HINT = "~/.mitmproxy/mitmproxy-ca-cert.pem"

        fun isValidHost(h: String): Boolean {
            val t = h.trim()
            if (t.isBlank()) return false
            if (t == "0.0.0.0" || t == "localhost" || t == "*") return true
            // Reuse Scope helpers without importing the whole engagement model here.
            return t.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) ||
                t.matches(Regex("""^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$"""))
        }
        fun isValidPort(p: Int): Boolean = p in 1..65535
    }
}

class MitmConfigRepo(private val ctx: Context) {
    private object K {
        val host = stringPreferencesKey("listen_host")
        val port = intPreferencesKey("listen_port")
        val flow = stringPreferencesKey("flow_path")
        val jsonl = stringPreferencesKey("jsonl_path")
    }

    val config: Flow<MitmConfig> = ctx.mitmDataStore.data.map { p ->
        MitmConfig(
            listenHost = p[K.host] ?: MitmConfig.DEFAULT_HOST,
            listenPort = p[K.port] ?: MitmConfig.DEFAULT_PORT,
            flowPath = p[K.flow] ?: MitmConfig.DEFAULT_FLOW_PATH,
            jsonlPath = p[K.jsonl] ?: MitmConfig.DEFAULT_JSONL_PATH,
        )
    }

    suspend fun current(): MitmConfig = config.first()

    suspend fun save(c: MitmConfig) = ctx.mitmDataStore.edit {
        it[K.host] = c.listenHost.trim()
        it[K.port] = c.listenPort
        it[K.flow] = c.flowPath.trim()
        it[K.jsonl] = c.jsonlPath.trim()
    }
}
