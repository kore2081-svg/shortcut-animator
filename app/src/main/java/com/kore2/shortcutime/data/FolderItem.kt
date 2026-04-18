package com.kore2.shortcutime.data

import java.util.UUID

data class FolderItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val settings: FolderSettings = FolderSettings(),
    val shortcuts: List<ShortcutEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
