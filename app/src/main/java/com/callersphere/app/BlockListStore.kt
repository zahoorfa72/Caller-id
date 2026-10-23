package com.callersphere.app

import android.content.Context

/**
 * Stores explicitly blocked phone numbers on-device.
 * The number list is never synchronized to Supabase.
 */
object BlockListStore {
    private const val PREFS = "callersphere_local_settings"
    private const val KEY_BLOCKED = "blocked_numbers"

    fun normalize(raw: String): String = raw.filter { it.isDigit() || it == '+' }

    private fun digits(raw: String): String = raw.filter(Char::isDigit)

    fun getAll(context: Context): Set<String> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        return stored.toSet()
    }

    fun add(context: Context, raw: String) {
        val number = normalize(raw)
        if (digits(number).length < 5) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()).toMutableSet()
        updated.add(number)
        prefs.edit().putStringSet(KEY_BLOCKED, updated).apply()
    }

    fun remove(context: Context, raw: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()).toMutableSet()
        updated.remove(normalize(raw))
        prefs.edit().putStringSet(KEY_BLOCKED, updated).apply()
    }

    fun isBlocked(context: Context, raw: String): Boolean {
        val incoming = digits(raw)
        if (incoming.isBlank()) return false
        return getAll(context).any { digits(it) == incoming }
    }
}
