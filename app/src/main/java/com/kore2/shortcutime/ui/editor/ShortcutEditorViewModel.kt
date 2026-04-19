package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.LanguageClassifier
import com.kore2.shortcutime.llm.LlmError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ShortcutEditorViewModel(
    private val repository: FolderRepository,
    private val generationService: ExampleGenerationService,
    val folderId: String,
    val shortcutId: String?,
) : ViewModel() {

    private val _entry = MutableStateFlow<ShortcutEntry?>(null)
    val entry: StateFlow<ShortcutEntry?> = _entry.asStateFlow()

    private val _savedShortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val savedShortcuts: StateFlow<List<ShortcutEntry>> = _savedShortcuts.asStateFlow()

    private val _workingExamples = MutableStateFlow<List<ExampleItem>>(emptyList())
    val workingExamples: StateFlow<List<ExampleItem>> = _workingExamples.asStateFlow()

    private val _folderMissing = MutableStateFlow(false)
    val folderMissing: StateFlow<Boolean> = _folderMissing.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            if (folder == null) { _folderMissing.value = true; return@launch }
            _savedShortcuts.value = folder.shortcuts.filterNot { it.id == shortcutId }
            val current = shortcutId?.let { id -> folder.shortcuts.firstOrNull { it.id == id } }
            _entry.value = current
            _workingExamples.value = current?.examples.orEmpty()
        }
    }

    fun refreshSavedShortcuts() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId) ?: return@launch
            _savedShortcuts.value = folder.shortcuts.filterNot { it.id == shortcutId }
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

    fun onGenerateExamplesClicked(shortcut: String, expansion: String, count: Int) {
        if (shortcut.isBlank() || expansion.isBlank()) {
            _events.tryEmit(EditorEvent.GenerateError(LlmError.Unknown("shortcut/expansion empty")))
            return
        }
        val remaining = MAX_EXAMPLES_PER_SHORTCUT - _workingExamples.value.size
        if (remaining <= 0) {
            _events.tryEmit(EditorEvent.ExampleCapReached)
            return
        }
        val actualCount = minOf(count, remaining)
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val outcome = generationService.generate(shortcut, expansion, actualCount)
                when (outcome) {
                    is ExampleGenerationService.Outcome.Success -> {
                        outcome.examples.forEach { addGeneratedExample(it) }
                        _events.emit(EditorEvent.GenerateSuccess(outcome.examples.size))
                    }
                    is ExampleGenerationService.Outcome.Partial -> {
                        outcome.examples.forEach { addGeneratedExample(it) }
                        _events.emit(EditorEvent.GeneratePartial(outcome.examples.size, outcome.requested))
                    }
                    is ExampleGenerationService.Outcome.Failure -> _events.emit(EditorEvent.GenerateError(outcome.error))
                    ExampleGenerationService.Outcome.NoActiveProvider -> _events.emit(EditorEvent.NoActiveProvider)
                    ExampleGenerationService.Outcome.NoKey -> _events.emit(EditorEvent.NoKey)
                    ExampleGenerationService.Outcome.DailyCapExceeded -> _events.emit(EditorEvent.DailyCapExceeded)
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun translateExample(item: ExampleItem) {
        viewModelScope.launch {
            val (sourceText, targetLanguage) = if (item.english.isNotBlank()) {
                item.english to "Korean"
            } else {
                item.korean to "English"
            }
            val outcome = generationService.translate(sourceText, targetLanguage)
            when (outcome) {
                is ExampleGenerationService.TranslationOutcome.Success -> {
                    val updated = if (item.english.isNotBlank()) {
                        item.copy(korean = outcome.translation)
                    } else {
                        item.copy(english = outcome.translation)
                    }
                    addOrUpdateExample(updated)
                    if (shortcutId != null) autoSave()
                }
                ExampleGenerationService.TranslationOutcome.NoActiveProvider -> _events.tryEmit(EditorEvent.NoActiveProvider)
                ExampleGenerationService.TranslationOutcome.NoKey -> _events.tryEmit(EditorEvent.NoKey)
                is ExampleGenerationService.TranslationOutcome.Failure -> _events.tryEmit(EditorEvent.GenerateError(outcome.error))
            }
        }
    }

    private fun addGeneratedExample(text: String) {
        val item = when (LanguageClassifier.classify(text)) {
            LanguageClassifier.Language.KOREAN -> ExampleItem(
                id = UUID.randomUUID().toString(),
                korean = text,
                english = "",
                sourceType = ExampleSourceType.AUTO,
            )
            LanguageClassifier.Language.ENGLISH -> ExampleItem(
                id = UUID.randomUUID().toString(),
                english = text,
                korean = "",
                sourceType = ExampleSourceType.AUTO,
            )
        }
        addOrUpdateExample(item)
        if (shortcutId != null) autoSave()
    }

    private fun autoSave() {
        val entry = _entry.value ?: return
        try {
            val updated = entry.copy(
                examples = _workingExamples.value,
                updatedAt = System.currentTimeMillis(),
            )
            repository.updateShortcut(folderId, updated)
        } catch (e: Exception) {
            // Ignore autoSave failures — user-initiated save still works
        }
    }

    fun save(
        shortcut: String, expandsTo: String, note: String,
        caseSensitive: Boolean, backspaceToUndo: Boolean,
    ): SaveResult {
        if (shortcut.isBlank()) return SaveResult.MissingShortcut
        if (expandsTo.isBlank()) return SaveResult.MissingExpandsTo
        return try {
            val current = _entry.value
            val updated = if (current == null) {
                ShortcutEntry(
                    shortcut = shortcut, expandsTo = expandsTo,
                    examples = _workingExamples.value, note = note,
                    caseSensitive = caseSensitive, backspaceToUndo = backspaceToUndo,
                )
            } else {
                current.copy(
                    shortcut = shortcut, expandsTo = expandsTo,
                    examples = _workingExamples.value, note = note,
                    caseSensitive = caseSensitive, backspaceToUndo = backspaceToUndo,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            if (current == null) repository.addShortcut(folderId, updated)
            else repository.updateShortcut(folderId, updated)
            SaveResult.Success
        } catch (e: Exception) {
            SaveResult.Error
        }
    }

    fun deleteShortcut(id: String) {
        viewModelScope.launch {
            repository.deleteShortcut(folderId, id)
            refreshSavedShortcuts()
        }
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object MissingShortcut : SaveResult()
        data object MissingExpandsTo : SaveResult()
        data object Error : SaveResult()
    }

    sealed class EditorEvent {
        data class GenerateSuccess(val addedCount: Int) : EditorEvent()
        data class GeneratePartial(val got: Int, val requested: Int) : EditorEvent()
        data class GenerateError(val error: LlmError) : EditorEvent()
        data object NoActiveProvider : EditorEvent()
        data object NoKey : EditorEvent()
        data object DailyCapExceeded : EditorEvent()
        data object ExampleCapReached : EditorEvent()
    }

    companion object {
        const val MAX_EXAMPLES_PER_SHORTCUT = 10
        fun factory(folderId: String, shortcutId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                ShortcutEditorViewModel(app.repository, app.exampleGenerationService, folderId, shortcutId)
            }
        }
        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }

}
