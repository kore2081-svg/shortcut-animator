package com.kore2.shortcutime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ShortcutRepository(context: Context) {
    private val prefs = context.getSharedPreferences("shortcut_store", Context.MODE_PRIVATE)

    fun getAll(): List<ShortcutItem> {
        val raw = prefs.getString(KEY_SHORTCUTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val shortcut = item.optString("shortcut").trim()
                val expandsTo = item.optString("expandsTo").trim()
                if (shortcut.isNotEmpty() && expandsTo.isNotEmpty()) {
                    add(ShortcutItem(shortcut, expandsTo))
                }
            }
        }.sortedBy { it.shortcut.lowercase() }
    }

    fun save(shortcut: String, expandsTo: String) {
        val normalizedShortcut = shortcut.trim()
        val normalizedExpandsTo = expandsTo.trim()
        if (normalizedShortcut.isEmpty() || normalizedExpandsTo.isEmpty()) return

        val updated = getAll()
            .filterNot { it.shortcut.equals(normalizedShortcut, ignoreCase = true) }
            .plus(ShortcutItem(normalizedShortcut, normalizedExpandsTo))
            .sortedBy { it.shortcut.lowercase() }

        write(updated)
    }

    fun delete(shortcut: String) {
        val updated = getAll().filterNot { it.shortcut.equals(shortcut, ignoreCase = true) }
        write(updated)
    }

    fun findExpansion(shortcut: String): String? {
        return getAll().firstOrNull { it.shortcut.equals(shortcut.trim(), ignoreCase = true) }?.expandsTo
    }

    fun findExpansionCandidate(prefix: String): ShortcutItem? {
        val normalizedPrefix = prefix.trim()
        if (normalizedPrefix.isEmpty()) return null
        return getAll().firstOrNull { it.shortcut.startsWith(normalizedPrefix, ignoreCase = true) }
    }

    fun findExpansionCandidates(prefix: String, limit: Int = 3): List<ShortcutItem> {
        val normalizedPrefix = prefix.trim()
        if (normalizedPrefix.isEmpty()) return emptyList()
        return getAll()
            .filter { it.shortcut.startsWith(normalizedPrefix, ignoreCase = true) }
            .take(limit)
    }

    private fun write(items: List<ShortcutItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("shortcut", item.shortcut)
                    put("expandsTo", item.expandsTo)
                },
            )
        }

        prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
    }

    companion object {
        private const val KEY_SHORTCUTS = "shortcuts"
    }
}
