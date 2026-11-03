package com.victorious.victordrax.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.victorious.victordrax.privilege.PrivilegeMode

val Context.dataStore by preferencesDataStore(name = "va_settings")

enum class AppTheme(val label: String) { NEON("Neon (default)"), SYNTHWAVE("Synthwave"), MATRIX("Matrix"), ICE("Ice") }

data class Settings(
    val theme: AppTheme = AppTheme.NEON,
    val favorites: Set<String> = emptySet(),
    val installPath: String = DEFAULT_INSTALL_PATH,
    val rootfsUrl: String = "",
    val rootfsSha256: String = "",
    val envInstalled: Boolean = false,
    val activeEngagementId: Long = 0L,
    val vncDisplay: String = ":1",
    val privilegeMode: PrivilegeMode = PrivilegeMode.AUTO,
)

const val DEFAULT_INSTALL_PATH = "/data/local/andrax"

class SettingsRepo(private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val favs = stringSetPreferencesKey("favorites")
        val path = stringPreferencesKey("install_path")
        val url = stringPreferencesKey("rootfs_url")
        val sha = stringPreferencesKey("rootfs_sha256")
        val installed = booleanPreferencesKey("env_installed")
        val active = longPreferencesKey("active_engagement")
        val vnc = stringPreferencesKey("vnc_display")
        val privilege = stringPreferencesKey("privilege_mode")
    }

    val settings: Flow<Settings> = ctx.dataStore.data.map { p ->
        Settings(
            theme = p[K.theme]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.NEON,
            favorites = p[K.favs] ?: emptySet(),
            installPath = p[K.path] ?: DEFAULT_INSTALL_PATH,
            rootfsUrl = p[K.url] ?: "",
            rootfsSha256 = p[K.sha] ?: "",
            envInstalled = p[K.installed] ?: false,
            activeEngagementId = p[K.active] ?: 0L,
            vncDisplay = p[K.vnc] ?: ":1",
            privilegeMode = p[K.privilege]?.let { runCatching { PrivilegeMode.valueOf(it) }.getOrNull() } ?: PrivilegeMode.AUTO,
        )
    }

    suspend fun current(): Settings = settings.first()
    suspend fun setTheme(t: AppTheme) = ctx.dataStore.edit { it[K.theme] = t.name }
    suspend fun toggleFavorite(id: String) = ctx.dataStore.edit {
        val cur = it[K.favs] ?: emptySet()
        it[K.favs] = if (id in cur) cur - id else cur + id
    }
    suspend fun setInstallPath(v: String) = ctx.dataStore.edit { it[K.path] = v }
    suspend fun setRootfs(url: String, sha: String) = ctx.dataStore.edit { it[K.url] = url.trim(); it[K.sha] = sha.trim() }
    suspend fun setInstalled(v: Boolean) = ctx.dataStore.edit { it[K.installed] = v }
    suspend fun setActiveEngagement(id: Long) = ctx.dataStore.edit { it[K.active] = id }
    suspend fun setVncDisplay(v: String) = ctx.dataStore.edit { it[K.vnc] = v }
    suspend fun setPrivilegeMode(m: PrivilegeMode) = ctx.dataStore.edit { it[K.privilege] = m.name }
}
