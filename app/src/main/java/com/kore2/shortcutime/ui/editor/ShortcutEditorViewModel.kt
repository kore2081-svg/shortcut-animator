package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.ShortcutEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShortcutEditorViewModel(
    private val repository: FolderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: String = requireNotNull(savedStateHandle["folderId"]) {
        "folderId argument is required"
    }
    val shortcutId: String? = savedStateHandle["shortcutId"]

    private val _entry = MutableStateFlow<ShortcutEntry?>(null)
    val entry: StateFlow<ShortcutEntry?> = _entry.asStateFlow()

    private val _savedShortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val savedShortcuts: StateFlow<List<ShortcutEntry>> = _savedShortcuts.asStateFlow()

    private val _workingExamples = MutableStateFlow<List<ExampleItem>>(emptyList())
    val workingExamples: StateFlow<List<ExampleItem>> = _workingExamples.asStateFlow()

    private val _folderMissing = MutableStateFlow(false)
    val folderMissing: StateFlow<Boolean> = _folderMissing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            if (folder == null) {
                _folderMissing.value = true
                return@launch
            }
            _savedShortcuts.value = folder.shortcuts
            val current = shortcutId?.let { id -> folder.shortcuts.firstOrNull { it.id == id } }
            _entry.value = current
            _workingExamples.value = current?.examples.orEmpty()
        }
    }

    fun refreshSavedShortcuts() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId) ?: return@launch
            _savedShortcuts.value = folder.shortcuts
        }
    }

    fun addOrUpdateExample(example: ExampleItem) {
        val list = _workingExamples.value.toMutableList()
        val index = list.indexOfFirst { it.id == example.id }
        if (index >= 0) list[index] = example else list.add(example)
        _workingExamples.value = list
    }

    fun deleteExample(id: String) {
        _workingExamples.value = _workingExamples.value.filterNot { it.id == id }
    }

    fun save(
        shortcut: String,
        expandsTo: String,
        note: String,
        caseSensitive: Boolean,
        backspaceToUndo: Boolean,
    ): SaveResult {
        if (shortcut.isBlank()) return SaveResult.MissingShortcut
        if (expandsTo.isBlank()) return SaveResult.MissingExpandsTo

        val current = _entry.value
        val updated = if (current == null) {
            ShortcutEntry(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = _workingExamples.value,
                note = note,
                caseSensitive = caseSensitive,
                backspaceToUndo = backspaceToUndo,
            )
        } else {
            current.copy(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = _workingExamples.value,
                note = note,
                caseSensitive = caseSensitive,
                backspaceToUndo = backspaceToUndo,
                updatedAt = System.currentTimeMillis(),
            )
        }

        if (current == null) {
            repository.addShortcut(folderId, updated)
        } else {
            repository.updateShortcut(folderId, updated)
        }
        return SaveResult.Success
    }

    fun deleteShortcut(id: String) {
        repository.deleteShortcut(folderId, id)
        refreshSavedShortcuts()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object MissingShortcut : SaveResult()
        data object MissingExpandsTo : SaveResult()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                ShortcutEditorViewModel(app.repository, createSavedStateHandle())
            }
        }

        private val APPLICATION_KEY = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
