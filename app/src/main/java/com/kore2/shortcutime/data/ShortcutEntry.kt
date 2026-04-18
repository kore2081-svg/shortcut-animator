package com.kore2.shortcutime.data

import java.util.UUID

data class ShortcutEntry(
    val id: String = UUID.randomUUID().toString(),
    val shortcut: String,
    val expandsTo: String,
    val usageCount: Int = 0,
    val examples: List<ExampleItem> = emptyList(),
    val note: String = "",
    val caseSensitive: Boolean = false,
    val backspaceToUndo: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val usageDisplay: String
        get() = if (usageCount > 100) "100↑" else usageCount.toString()
}
