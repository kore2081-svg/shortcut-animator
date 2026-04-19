package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutBinding

class ShortcutAdapter(
    private val onEdit: (ShortcutEntry) -> Unit,
    private val onDelete: (ShortcutEntry) -> Unit,
) : ListAdapter<ShortcutEntry, ShortcutAdapter.ShortcutViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<ShortcutEntry>?) {
        super.submitList(list?.sortedBy { it.shortcut.lowercase() })
    }

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore = ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: ShortcutEntry) {
            val theme = themeStore.currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.shortcutLabel.text = item.shortcut
            binding.shortcutLabel.setTextColor(theme.accentColor)
            binding.expandsToLabel.text = item.expandsTo
            binding.expandsToLabel.setTextColor(theme.textPrimary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setTextColor(theme.textSecondary)
            binding.deleteShortcutButton.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setOnClickListener { onEdit(item) }
            binding.deleteShortcutButton.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ShortcutEntry>() {
            override fun areItemsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old.id == new.id
            override fun areContentsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old == new
        }
    }
}
