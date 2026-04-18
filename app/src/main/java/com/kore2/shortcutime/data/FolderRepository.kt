package com.kore2.shortcutime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FolderRepository(context: Context) {
    private val prefs = context.getSharedPreferences("shortcut_store", Context.MODE_PRIVATE)

    fun getAllFolders(): List<FolderItem> {
        val raw = prefs.getString(KEY_FOLDERS, null)
        if (raw.isNullOrBlank()) {
            return migrateLegacyShortcutsIfNeeded()
        }
        return parseFolders(JSONArray(raw)).sortedBy { it.title.lowercase() }
    }

    fun getFolder(folderId: String): FolderItem? {
        return getAllFolders().firstOrNull { it.id == folderId }
    }

    fun saveFolder(folder: FolderItem) {
        val folders = getAllFolders().toMutableList()
        val index = folders.indexOfFirst { it.id == folder.id }
        val updatedFolder = folder.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            folders[index] = updatedFolder
        } else {
            folders.add(updatedFolder)
        }
        writeFolders(folders.sortedBy { it.title.lowercase() })
    }

    fun deleteFolder(folderId: String) {
        val updated = getAllFolders().filterNot { it.id == folderId }
        writeFolders(updated)
    }

    fun addShortcut(folderId: String, entry: ShortcutEntry) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = (folder.shortcuts + entry).sortedBy { it.shortcut.lowercase() },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun updateShortcut(folderId: String, entry: ShortcutEntry) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == entry.id) {
                        entry.copy(updatedAt = System.currentTimeMillis())
                    } else {
                        shortcut
                    }
                }.sortedBy { it.shortcut.lowercase() },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun deleteShortcut(folderId: String, shortcutId: String) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.filterNot { it.id == shortcutId },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun incrementUsage(folderId: String, shortcutId: String) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == shortcutId) {
                        shortcut.copy(
                            usageCount = shortcut.usageCount + 1,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        shortcut
                    }
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun addExample(folderId: String, shortcutId: String, example: ExampleItem) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == shortcutId) {
                        shortcut.copy(
                            examples = shortcut.examples + example,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        shortcut
                    }
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun updateExample(folderId: String, shortcutId: String, example: ExampleItem) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == shortcutId) {
                        shortcut.copy(
                            examples = shortcut.examples.map {
                                if (it.id == example.id) example else it
                            },
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        shortcut
                    }
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun deleteExample(folderId: String, shortcutId: String, exampleId: String) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == shortcutId) {
                        shortcut.copy(
                            examples = shortcut.examples.filterNot { it.id == exampleId },
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        shortcut
                    }
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun replaceAutoExamples(folderId: String, shortcutId: String, newAutoExamples: List<ExampleItem>) {
        val updated = getAllFolders().map { folder ->
            if (folder.id != folderId) return@map folder
            folder.copy(
                shortcuts = folder.shortcuts.map { shortcut ->
                    if (shortcut.id == shortcutId) {
                        val manualExamples = shortcut.examples.filter { it.sourceType == ExampleSourceType.MANUAL }
                        shortcut.copy(
                            examples = manualExamples + newAutoExamples,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        shortcut
                    }
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
        writeFolders(updated)
    }

    fun findMatchingShortcut(token: String): ShortcutMatch? {
        val normalized = token.trim()
        if (normalized.isEmpty()) return null

        getAllFolders().forEach { folder ->
            folder.shortcuts.forEach { shortcut ->
                val matched = if (shortcut.caseSensitive) {
                    shortcut.shortcut == normalized
                } else {
                    shortcut.shortcut.equals(normalized, ignoreCase = true)
                }
                if (matched) {
                    return ShortcutMatch(folder, shortcut)
                }
            }
        }
        return null
    }

    fun findMatchingCandidates(prefix: String, limit: Int = 3): List<ShortcutMatch> {
        val normalized = prefix.trim()
        if (normalized.isEmpty()) return emptyList()

        val matches = mutableListOf<ShortcutMatch>()
        getAllFolders().forEach { folder ->
            folder.shortcuts.forEach { shortcut ->
                val matched = if (shortcut.caseSensitive) {
                    shortcut.shortcut.startsWith(normalized)
                } else {
                    shortcut.shortcut.startsWith(normalized, ignoreCase = true)
                }
                if (matched) {
                    matches.add(ShortcutMatch(folder, shortcut))
                }
            }
        }
        return matches.sortedBy { it.entry.shortcut.lowercase() }.take(limit)
    }

    private fun writeFolders(folders: List<FolderItem>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(folder.toJson())
        }
        prefs.edit().putString(KEY_FOLDERS, array.toString()).apply()
    }

    private fun parseFolders(array: JSONArray): List<FolderItem> {
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(obj.toFolderItem())
            }
        }
    }

    private fun migrateLegacyShortcutsIfNeeded(): List<FolderItem> {
        val legacyRaw = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        val legacyArray = JSONArray(legacyRaw)
        val shortcuts = buildList {
            for (i in 0 until legacyArray.length()) {
                val obj = legacyArray.optJSONObject(i) ?: continue
                val shortcut = obj.optString("shortcut").trim()
                val expandsTo = obj.optString("expandsTo").trim()
                if (shortcut.isNotEmpty() && expandsTo.isNotEmpty()) {
                    add(
                        ShortcutEntry(
                            id = UUID.randomUUID().toString(),
                            shortcut = shortcut,
                            expandsTo = expandsTo,
                        ),
                    )
                }
            }
        }
        if (shortcuts.isEmpty()) return emptyList()

        val defaultFolder = FolderItem(
            id = UUID.randomUUID().toString(),
            title = "기본 폴더",
            shortcuts = shortcuts.sortedBy { it.shortcut.lowercase() },
        )
        writeFolders(listOf(defaultFolder))
        prefs.edit().remove(KEY_SHORTCUTS).apply()
        return listOf(defaultFolder)
    }

    companion object {
        private const val KEY_FOLDERS = "folders"
        private const val KEY_SHORTCUTS = "shortcuts"
    }
}

private fun FolderItem.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("title", title)
        put("note", note)
        put("settings", settings.toJson())
        put("shortcuts", JSONArray().apply {
            shortcuts.forEach { put(it.toJson()) }
        })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
}

private fun FolderSettings.toJson(): JSONObject {
    return JSONObject().apply {
        put("prefixMode", prefixMode)
        put("expansionMode", expansionMode)
        put("appScope", appScope)
    }
}

private fun ShortcutEntry.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("shortcut", shortcut)
        put("expandsTo", expandsTo)
        put("usageCount", usageCount)
        put("examples", JSONArray().apply {
            examples.forEach { put(it.toJson()) }
        })
        put("note", note)
        put("caseSensitive", caseSensitive)
        put("backspaceToUndo", backspaceToUndo)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }
}

private fun ExampleItem.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("english", english)
        put("korean", korean)
        put("sourceType", sourceType.name)
    }
}

private fun JSONObject.toFolderItem(): FolderItem {
    val settingsObj = optJSONObject("settings") ?: JSONObject()
    val shortcutsArray = optJSONArray("shortcuts") ?: JSONArray()
    val shortcuts = buildList {
        for (i in 0 until shortcutsArray.length()) {
            val item = shortcutsArray.optJSONObject(i) ?: continue
            add(item.toShortcutEntry())
        }
    }

    return FolderItem(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        title = optString("title").ifBlank { "기본 폴더" },
        note = optString("note"),
        settings = settingsObj.toFolderSettings(),
        shortcuts = shortcuts,
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )
}

private fun JSONObject.toFolderSettings(): FolderSettings {
    return FolderSettings(
        prefixMode = optString("prefixMode", "ALL_CHARS"),
        expansionMode = optString("expansionMode", "SPACE_TRIGGER"),
        appScope = optString("appScope", "ALL_APPS"),
    )
}

private fun JSONObject.toShortcutEntry(): ShortcutEntry {
    val examplesArray = optJSONArray("examples") ?: JSONArray()
    val examples = buildList {
        for (i in 0 until examplesArray.length()) {
            val item = examplesArray.optJSONObject(i) ?: continue
            add(item.toExampleItem())
        }
    }

    return ShortcutEntry(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        shortcut = optString("shortcut").trim(),
        expandsTo = optString("expandsTo").trim(),
        usageCount = optInt("usageCount", 0),
        examples = examples,
        note = optString("note"),
        caseSensitive = optBoolean("caseSensitive", false),
        backspaceToUndo = optBoolean("backspaceToUndo", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
    )
}

private fun JSONObject.toExampleItem(): ExampleItem {
    val type = runCatching {
        ExampleSourceType.valueOf(optString("sourceType", ExampleSourceType.MANUAL.name))
    }.getOrDefault(ExampleSourceType.MANUAL)

    return ExampleItem(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        english = optString("english"),
        korean = optString("korean"),
        sourceType = type,
    )
}
