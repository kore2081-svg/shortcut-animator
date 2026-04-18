package com.kore2.shortcutime.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ActivityFolderDetailBinding

class FolderDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFolderDetailBinding
    private lateinit var repository: FolderRepository
    private lateinit var themeStore: KeyboardThemeStore
    private lateinit var adapter: ShortcutEntryAdapter
    private var folderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FolderRepository(this)
        themeStore = KeyboardThemeStore(this)
        folderId = intent.getStringExtra(EXTRA_FOLDER_ID).orEmpty()

        adapter = ShortcutEntryAdapter(
            onShortcutClick = { openShortcutEditor(it.id) },
            onShortcutDelete = { confirmDelete(it) },
        )

        binding.shortcutRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.shortcutRecyclerView.adapter = adapter
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { finish() }
        binding.addShortcutFab.setOnClickListener { openShortcutEditor(null) }

        refreshFolder()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshFolder()
    }

    private fun applyTheme() {
        val theme = themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.folderHeaderText.setTextColor(theme.textSecondary)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addShortcutFab, theme)
    }

    private fun refreshFolder() {
        val folder = repository.getFolder(folderId) ?: run {
            finish()
            return
        }
        binding.topToolbar.title = folder.title
        binding.folderHeaderText.text = getString(R.string.folder_header_format, folder.title, folder.shortcuts.size)
        adapter.submitList(folder.shortcuts)
        binding.emptyStateText.visibility = if (folder.shortcuts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openShortcutEditor(shortcutId: String?) {
        startActivity(
            Intent(this, ShortcutEditorActivity::class.java).apply {
                putExtra(EXTRA_FOLDER_ID, folderId)
                shortcutId?.let { putExtra(EXTRA_SHORTCUT_ID, it) }
            },
        )
    }

    private fun confirmDelete(shortcut: ShortcutEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, shortcut.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                repository.deleteShortcut(folderId, shortcut.id)
                refreshFolder()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_FOLDER_ID = "extra_folder_id"
        const val EXTRA_SHORTCUT_ID = "extra_shortcut_id"
    }
}
