package com.kore2.shortcutime.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderDetailViewModel(
    private val repository: FolderRepository,
    val folderId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<FolderDetailState>(FolderDetailState.Loading)
    val state: StateFlow<FolderDetailState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            _state.value = if (folder == null) {
                FolderDetailState.NotFound
            } else {
                FolderDetailState.Loaded(folder)
            }
        }
    }

    fun deleteShortcut(shortcutId: String) {
        viewModelScope.launch {
            repository.deleteShortcut(folderId, shortcutId)
            refresh()
        }
    }

    companion object {
        fun factory(folderId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                FolderDetailViewModel(app.repository, folderId)
            }
        }

        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}

sealed class FolderDetailState {
    data object Loading : FolderDetailState()
    data object NotFound : FolderDetailState()
    data class Loaded(val folder: FolderItem) : FolderDetailState()
}
