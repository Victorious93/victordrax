package com.victorious.victordrax.pairing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Own small DataStore so this feature does not touch the shared settings schema. */
private val Context.pairingStore by preferencesDataStore(name = "wireless_pairing")

/** Remembers ONLY the last-used device host (an IP on the user's LAN). Never the pairing code or ports. */
class PairingPrefs(private val ctx: Context) {
    private val lastHost = stringPreferencesKey("last_host")
    val host: Flow<String> = ctx.pairingStore.data.map { it[lastHost] ?: "" }
    suspend fun saveHost(h: String) {
        if (WirelessAdb.isValidHost(h)) ctx.pairingStore.edit { it[lastHost] = h }
    }
}
