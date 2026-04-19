package com.kore2.shortcutime.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderListViewModel(
    private val repository: FolderRepository,
) : ViewModel() {

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _folders.value = repository.getAllFolders()
        }
    }

    fun deleteFolder(id: String) {
        repository.deleteFolder(id)
        refresh()
    }

    class Factory(private val app: ShortcutApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FolderListViewModel(app.repository) as T
        }
    }
}
