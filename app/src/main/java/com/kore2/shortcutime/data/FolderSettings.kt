package com.kore2.shortcutime.data

data class FolderSettings(
    val prefixMode: String = "ALL_CHARS",
    val expansionMode: String = "SPACE_TRIGGER",
    val appScope: String = "ALL_APPS",
)
