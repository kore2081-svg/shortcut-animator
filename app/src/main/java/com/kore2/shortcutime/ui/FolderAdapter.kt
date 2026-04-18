package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (FolderItem) -> Unit,
    private val onFolderEdit: (FolderItem) -> Unit,
    private val onFolderDelete: (FolderItem) -> Unit,
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {
    private val items = mutableListOf<FolderItem>()

    fun submitList(newItems: List<FolderItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FolderItem) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
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
            binding.openButton.setTextColor(theme.accentColor)
            binding.root.setOnClickListener { openAction() }
            binding.folderContentArea.setOnClickListener { openAction() }
            binding.openButton.setOnClickListener { openAction() }
            binding.root.setOnLongClickListener {
                onFolderEdit(item)
                true
            }
        }
    }
}
