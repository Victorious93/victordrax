package com.victorious.victordrax.schedule

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Own DataStore file, so schedules never touch the Room schema / findings tables. */
private val Context.scheduleStore by preferencesDataStore(name = "scan_schedules")

class ScheduleStore(private val ctx: Context) {
    private val key = stringPreferencesKey("schedules_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(ScheduledScan.serializer())

    private fun decode(s: String?): List<ScheduledScan> =
        s?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() } ?: emptyList()

    val all: Flow<List<ScheduledScan>> = ctx.scheduleStore.data.map { p -> decode(p[key]).sortedByDescending { it.createdAt } }

    suspend fun allOnce(): List<ScheduledScan> = all.first()
    suspend fun get(id: String): ScheduledScan? = allOnce().firstOrNull { it.id == id }

    suspend fun upsert(s: ScheduledScan) = ctx.scheduleStore.edit { p ->
        val cur = decode(p[key]).filterNot { it.id == s.id }
        p[key] = json.encodeToString(ser, cur + s)
    }

    suspend fun remove(id: String) = ctx.scheduleStore.edit { p ->
        p[key] = json.encodeToString(ser, decode(p[key]).filterNot { it.id == id })
    }
}
