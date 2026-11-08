package com.victorious.victordrax.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Own DataStore file for the last discovery snapshot. Deliberately NOT Room / tool_runs —
 * another feature owns tool_runs schema migrations.
 */
private val Context.networkStore by preferencesDataStore(name = "network_discovery")

class NetworkStore(private val ctx: Context) {
    private object K {
        val snapshot = stringPreferencesKey("snapshot_json")
    }

    val snapshot: Flow<DiscoverySnapshot?> = ctx.networkStore.data.map { p ->
        p[K.snapshot]?.let { runCatching { HostLogic.fromJson(it) }.getOrNull() }
    }

    suspend fun current(): DiscoverySnapshot? = snapshot.first()

    suspend fun save(s: DiscoverySnapshot) = ctx.networkStore.edit {
        it[K.snapshot] = HostLogic.toJson(s)
    }

    suspend fun clear() = ctx.networkStore.edit { it.remove(K.snapshot) }
}
