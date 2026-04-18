package com.kore2.shortcutime.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: FolderRepository
    private lateinit var themeStore: KeyboardThemeStore
    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FolderRepository(this)
        themeStore = KeyboardThemeStore(this)
        adapter = FolderAdapter(
            onFolderClick = { openFolderDetail(it) },
            onFolderEdit = { openFolderEditor(it.id) },
            onFolderDelete = { confirmDelete(it) },
        )

        binding.folderRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.folderRecyclerView.adapter = adapter
        binding.addFolderFab.setOnClickListener { openFolderEditor(null) }
        binding.openAnimatorScreenButton.setOnClickListener { openAnimatorScreen() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        refreshFolders()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshFolders()
    }

    private fun applyTheme() {
        val theme = themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.bottomTabBar.setBackgroundColor(theme.keyboardBackground)
        applyFilledButtonTheme(binding.foldersButton, theme)
        applyFilledButtonTheme(binding.settingsButton, theme)
        applyFilledButtonTheme(binding.openAnimatorScreenButton, theme)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addFolderFab, theme)
    }

    private fun refreshFolders() {
        val folders = repository.getAllFolders()
        adapter.submitList(folders)
        val isEmpty = folders.isEmpty()
        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.openAnimatorScreenButton.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            binding.emptyStateText.text = getString(com.kore2.shortcutime.R.string.empty_folders_detail)
        }
    }

    private fun openFolderDetail(folder: FolderItem) {
        startActivity(
            Intent(this, FolderDetailActivity::class.java).apply {
                putExtra(FolderDetailActivity.EXTRA_FOLDER_ID, folder.id)
            },
        )
    }

    private fun openFolderEditor(folderId: String?) {
        startActivity(
            Intent(this, FolderEditorActivity::class.java).apply {
                folderId?.let { putExtra(FolderEditorActivity.EXTRA_FOLDER_ID, it) }
            },
        )
    }

    private fun openAnimatorScreen() {
        startActivity(Intent(this, ShortcutEditorActivity::class.java))
    }

    private fun confirmDelete(folder: FolderItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(com.kore2.shortcutime.R.string.dialog_delete_folder_title))
            .setMessage(getString(com.kore2.shortcutime.R.string.dialog_delete_folder_message, folder.title))
            .setPositiveButton(com.kore2.shortcutime.R.string.action_delete) { _, _ ->
                repository.deleteFolder(folder.id)
                refreshFolders()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
