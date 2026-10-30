package com.victorious.andrax

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var legalAccepted: Boolean
        get() = sp.getBoolean("legal_accepted", false)
        set(v) = sp.edit().putBoolean("legal_accepted", v).apply()
    var installPath: String
        get() = sp.getString("install_path", "/data/local/andrax") ?: "/data/local/andrax"
        set(v) = sp.edit().putString("install_path", v).apply()
}

const val LEGAL_NOTICE =
    "Victorious ANDRAX is for AUTHORIZED security testing only. Use it only on systems " +
    "you own or for which you have explicit written permission to test. Unauthorized " +
    "access to computer systems is illegal. You alone are responsible for how you use it. " +
    "The software is provided \"AS IS\", without warranty of any kind."
