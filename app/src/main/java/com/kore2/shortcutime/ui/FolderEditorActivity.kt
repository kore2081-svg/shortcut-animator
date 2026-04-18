package com.kore2.shortcutime.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ActivityFolderEditorBinding

class FolderEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFolderEditorBinding
    private lateinit var repository: FolderRepository
    private lateinit var themeStore: KeyboardThemeStore
    private var editingFolder: FolderItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FolderRepository(this)
        themeStore = KeyboardThemeStore(this)
        val folderId = intent.getStringExtra(EXTRA_FOLDER_ID)
        editingFolder = folderId?.let { repository.getFolder(it) }

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { finish() }
        editingFolder?.let {
            binding.topToolbar.title = getString(R.string.title_edit_folder)
            bindFolder(it)
        }
        binding.saveButton.setOnClickListener { saveFolder() }
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun applyTheme() {
        val theme = themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.titleInputLayout, binding.titleInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
    }

    private fun bindFolder(folder: FolderItem) {
        binding.titleInput.setText(folder.title)
        binding.noteInput.setText(folder.note)
    }

    private fun saveFolder() {
        val title = binding.titleInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()
        if (title.isBlank()) {
            binding.titleInputLayout.error = getString(R.string.error_folder_title_required)
            return
        }
        binding.titleInputLayout.error = null

        val folder = editingFolder?.copy(
            title = title,
            note = note,
            updatedAt = System.currentTimeMillis(),
        ) ?: FolderItem(
            title = title,
            note = note,
        )

        repository.saveFolder(folder)
        finish()
    }

    companion object {
        const val EXTRA_FOLDER_ID = "extra_folder_id"
    }
}
