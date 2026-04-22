package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (FolderItem) -> Unit,
    private val onFolderEdit: (FolderItem) -> Unit,
    private val onFolderDelete: (FolderItem) -> Unit,
) : ListAdapter<FolderItem, FolderAdapter.FolderViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore =
            ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: FolderItem) {
            val theme = themeStore.currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            val openAction = { onFolderClick(item) }
            binding.folderIcon.setTextColor(theme.textSecondary)
            binding.folderTitle.text = item.title
            binding.folderTitle.setTextColor(theme.textPrimary)
            binding.folderCount.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.folder_count_format,
                item.shortcuts.size,
            )
            binding.folderNote.text = item.note.ifBlank {
                binding.root.context.getString(com.kore2.shortcutime.R.string.folder_note_placeholder)
            }
            binding.folderCount.setTextColor(theme.textSecondary)
            binding.folderNote.setTextColor(theme.textSecondary)
            binding.openButton.setTextColor(theme.textPrimary)
            binding.root.setOnClickListener { openAction() }
            binding.folderContentArea.setOnClickListener { openAction() }
            binding.openButton.setOnClickListener { openAction() }
            binding.root.setOnLongClickListener {
                onFolderEdit(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FolderItem>() {
            override fun areItemsTheSame(old: FolderItem, new: FolderItem) = old.id == new.id
            override fun areContentsTheSame(old: FolderItem, new: FolderItem) = old == new
        }
    }
}
