package com.kore2.shortcutime.ui

import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.ime.ColemakPreviewView
import com.kore2.shortcutime.databinding.ActivityShortcutEditorBinding
import com.kore2.shortcutime.databinding.DialogExampleEditorBinding

class ShortcutEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityShortcutEditorBinding
    private lateinit var repository: FolderRepository
    private lateinit var themeStore: KeyboardThemeStore
    private lateinit var exampleAdapter: ExampleAdapter
    private lateinit var savedShortcutAdapter: ShortcutAdapter
    private lateinit var previewView: ColemakPreviewView

    private var folderId: String = ""
    private var editingEntry: ShortcutEntry? = null
    private var selectedGenerateCount: Int = 1
    private val workingExamples = mutableListOf<ExampleItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShortcutEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FolderRepository(this)
        themeStore = KeyboardThemeStore(this)
        previewView = binding.colemakPreview
        folderId = intent.getStringExtra(EXTRA_FOLDER_ID).orEmpty()
        previewView.setOnAnimationFinishedListener {
            binding.previewStatus.text = getString(R.string.preview_complete, it)
        }

        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        editingEntry = shortcutId?.let { id ->
            repository.getFolder(folderId)?.shortcuts?.firstOrNull { it.id == id }
        }

        exampleAdapter = ExampleAdapter(
            onEdit = { openExampleDialog(it) },
            onDelete = {
                workingExamples.removeAll { example -> example.id == it.id }
                refreshExamples()
            },
        )
        savedShortcutAdapter = ShortcutAdapter(
            onEdit = { openExistingShortcut(it.id) },
            onDelete = { deleteExistingShortcut(it) },
        )

        binding.examplesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.examplesRecyclerView.adapter = exampleAdapter
        binding.savedShortcutsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.savedShortcutsRecyclerView.adapter = savedShortcutAdapter
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { finish() }
        if (folderId.isBlank()) {
            binding.topToolbar.title = getString(R.string.title_manage_shortcuts)
        }

        editingEntry?.let {
            binding.topToolbar.title = getString(R.string.title_edit_shortcut)
            bindEntry(it)
        }

        setupGenerateCountButtons()
        binding.addExampleButton.setOnClickListener { openExampleDialog(null) }
        binding.generateExamplesButton.setOnClickListener { generateExamples(selectedGenerateCount) }
        binding.saveButton.setOnClickListener { saveShortcut() }
        binding.shortcutInput.addTextChangedListener { text ->
            updatePreview(text?.toString().orEmpty())
        }
        applyTheme()
        refreshExamples()
        updatePreview(binding.shortcutInput.text?.toString().orEmpty())
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshSavedShortcuts()
    }

    private fun bindEntry(entry: ShortcutEntry) {
        binding.shortcutInput.setText(entry.shortcut)
        binding.expandsToInput.setText(entry.expandsTo)
        binding.noteInput.setText(entry.note)
        binding.caseSensitiveSwitch.isChecked = entry.caseSensitive
        binding.backspaceUndoSwitch.isChecked = entry.backspaceToUndo
        workingExamples.clear()
        workingExamples.addAll(entry.examples)
    }

    private fun setupGenerateCountButtons() {
        binding.generateOneButton.setOnClickListener {
            selectedGenerateCount = 1
            updateGenerateSelection()
        }
        binding.generateThreeButton.setOnClickListener {
            selectedGenerateCount = 3
            updateGenerateSelection()
        }
        binding.generateFiveButton.setOnClickListener {
            selectedGenerateCount = 5
            updateGenerateSelection()
        }
        updateGenerateSelection()
    }

    private fun updateGenerateSelection() {
        val buttons = listOf(
            binding.generateOneButton to 1,
            binding.generateThreeButton to 3,
            binding.generateFiveButton to 5,
        )
        buttons.forEach { (button, count) ->
            button.isChecked = count == selectedGenerateCount
        }
    }

    private fun refreshExamples() {
        exampleAdapter.submitList(workingExamples.toList())
        binding.exampleCountText.text = getString(R.string.example_count_format, workingExamples.size)
    }

    private fun refreshSavedShortcuts() {
        val items = repository.getFolder(folderId)?.shortcuts.orEmpty()
        savedShortcutAdapter.submitList(items)
        binding.savedShortcutsRecyclerView.visibility = if (items.isEmpty()) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }

    private fun applyTheme() {
        val theme = themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.shortcutInputLayout, binding.shortcutInput, theme)
        applyInputLayoutTheme(binding.expandsToInputLayout, binding.expandsToInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        binding.previewCard.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.previewCard)
        binding.previewTitle.setTextColor(theme.textPrimary)
        binding.previewStatus.setTextColor(theme.textSecondary)
        binding.helperText.setTextColor(theme.textSecondary)
        binding.savedShortcutsTitle.setTextColor(theme.textPrimary)
        previewView.applyTheme(theme)
        applyBodyTextTheme(binding.examplesTitle, theme, emphasize = true)
        applyBodyTextTheme(binding.exampleCountText, theme)
        applyBodyTextTheme(binding.keywordSettingsTitle, theme, emphasize = true)
        applyFilledButtonTheme(binding.addExampleButton, theme)
        applyFilledButtonTheme(binding.generateOneButton, theme)
        applyFilledButtonTheme(binding.generateThreeButton, theme)
        applyFilledButtonTheme(binding.generateFiveButton, theme)
        applyFilledButtonTheme(binding.generateExamplesButton, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
        applySwitchTheme(binding.backspaceUndoSwitch, theme)
        applySwitchTheme(binding.caseSensitiveSwitch, theme)
    }

    private fun updatePreview(shortcut: String) {
        val normalized = shortcut.trim()
        if (normalized.isBlank()) {
            previewView.clearAnimationState()
            binding.previewStatus.text = getString(R.string.preview_idle)
            return
        }
        previewView.playSequence(normalized, 1000L)
        binding.previewStatus.text = getString(R.string.preview_animating, normalized)
    }

    private fun openExampleDialog(example: ExampleItem?) {
        val dialogBinding = DialogExampleEditorBinding.inflate(LayoutInflater.from(this))
        dialogBinding.englishInput.setText(example?.english.orEmpty())
        dialogBinding.koreanInput.setText(example?.korean.orEmpty())
        val theme = themeStore.currentTheme()
        dialogBinding.root.setBackgroundColor(theme.appBackground)
        applyInputLayoutTheme(dialogBinding.englishInputLayout, dialogBinding.englishInput, theme)
        applyInputLayoutTheme(dialogBinding.koreanInputLayout, dialogBinding.koreanInput, theme)

        AlertDialog.Builder(this)
            .setTitle(if (example == null) R.string.title_add_example else R.string.title_edit_example)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val english = dialogBinding.englishInput.text?.toString().orEmpty().trim()
                val korean = dialogBinding.koreanInput.text?.toString().orEmpty().trim()
                if (english.isBlank() && korean.isBlank()) return@setPositiveButton

                val updated = ExampleItem(
                    id = example?.id ?: java.util.UUID.randomUUID().toString(),
                    english = english,
                    korean = korean,
                    sourceType = example?.sourceType ?: ExampleSourceType.MANUAL,
                )

                if (example == null) {
                    workingExamples.add(updated)
                } else {
                    val index = workingExamples.indexOfFirst { it.id == example.id }
                    if (index >= 0) workingExamples[index] = updated
                }
                refreshExamples()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateExamples(count: Int) {
        val expandsTo = binding.expandsToInput.text?.toString().orEmpty().trim()
        if (expandsTo.isBlank()) {
            binding.expandsToInputLayout.error = getString(R.string.error_expands_to_required)
            return
        }
        binding.expandsToInputLayout.error = null

        val generated = (1..count).map { index ->
            ExampleItem(
                english = when (index % 3) {
                    1 -> "I can use \"$expandsTo\" in a natural conversation."
                    2 -> "Try saying \"$expandsTo\" when you want to sound more fluent."
                    else -> "\"$expandsTo\" fits well in everyday English."
                },
                korean = when (index % 3) {
                    1 -> "\"$expandsTo\"를 자연스러운 대화에서 사용할 수 있어요."
                    2 -> "더 자연스럽게 말하고 싶을 때 \"$expandsTo\"를 써보세요."
                    else -> "\"$expandsTo\"는 일상 영어에서 잘 어울리는 표현이에요."
                },
                sourceType = ExampleSourceType.AUTO,
            )
        }

        val manualOnly = workingExamples.filter { it.sourceType == ExampleSourceType.MANUAL }
        workingExamples.clear()
        workingExamples.addAll(manualOnly + generated)
        refreshExamples()
    }

    private fun saveShortcut() {
        val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
        val expandsTo = binding.expandsToInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()

        if (shortcut.isBlank()) {
            binding.shortcutInputLayout.error = getString(R.string.error_shortcut_required)
            return
        }
        if (expandsTo.isBlank()) {
            binding.expandsToInputLayout.error = getString(R.string.error_expands_to_required)
            return
        }
        binding.shortcutInputLayout.error = null
        binding.expandsToInputLayout.error = null

        val targetFolderId = ensureTargetFolderId()

        val current = editingEntry
        val entry = if (current == null) {
            ShortcutEntry(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = workingExamples.toList(),
                note = note,
                caseSensitive = binding.caseSensitiveSwitch.isChecked,
                backspaceToUndo = binding.backspaceUndoSwitch.isChecked,
            )
        } else {
            current.copy(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = workingExamples.toList(),
                note = note,
                caseSensitive = binding.caseSensitiveSwitch.isChecked,
                backspaceToUndo = binding.backspaceUndoSwitch.isChecked,
                updatedAt = System.currentTimeMillis(),
            )
        }

        if (current == null) {
            repository.addShortcut(targetFolderId, entry)
        } else {
            repository.updateShortcut(targetFolderId, entry)
        }
        refreshSavedShortcuts()
        finish()
    }

    private fun ensureTargetFolderId(): String {
        if (folderId.isNotBlank()) return folderId
        val existing = repository.getAllFolders().firstOrNull()
        if (existing != null) {
            folderId = existing.id
            return existing.id
        }
        val created = com.kore2.shortcutime.data.FolderItem(title = "Starter Folder")
        repository.saveFolder(created)
        folderId = created.id
        return created.id
    }

    private fun openExistingShortcut(shortcutId: String) {
        if (editingEntry?.id == shortcutId) return
        intent.putExtra(EXTRA_SHORTCUT_ID, shortcutId)
        recreate()
    }

    private fun deleteExistingShortcut(entry: ShortcutEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, entry.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                repository.deleteShortcut(folderId, entry.id)
                if (editingEntry?.id == entry.id) {
                    finish()
                } else {
                    refreshSavedShortcuts()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
    }
}
