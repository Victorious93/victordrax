package com.victorious.victordrax.mitm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Captured flows live in their own DataStore file — never in tool_runs / Room. */
private val Context.mitmFlowsStore by preferencesDataStore(name = "mitm_flows")

class MitmStore(private val ctx: Context) {
    private object K {
        val flows = stringPreferencesKey("flows_json")
    }

    val flows: Flow<List<MitmFlow>> = ctx.mitmFlowsStore.data.map { p ->
        p[K.flows]?.let { runCatching { MitmFlowLogic.fromJson(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun current(): List<MitmFlow> = flows.first()

    suspend fun save(list: List<MitmFlow>) = ctx.mitmFlowsStore.edit {
        it[K.flows] = MitmFlowLogic.toJson(list)
    }

    suspend fun clear() = ctx.mitmFlowsStore.edit { it.remove(K.flows) }
}
