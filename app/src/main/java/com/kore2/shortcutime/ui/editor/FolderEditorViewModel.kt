package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository

class FolderEditorViewModel(
    private val repository: FolderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: String? = savedStateHandle["folderId"]

    val existing: FolderItem? = folderId?.let { repository.getFolder(it) }

    fun save(title: String, note: String): Boolean {
        if (title.isBlank()) return false
        val folder = existing?.copy(
            title = title,
            note = note,
            updatedAt = System.currentTimeMillis(),
        ) ?: FolderItem(title = title, note = note)
        repository.saveFolder(folder)
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                FolderEditorViewModel(app.repository, createSavedStateHandle())
            }
        }

        private val APPLICATION_KEY = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
