package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutBinding

class ShortcutAdapter(
    private val onEdit: (ShortcutEntry) -> Unit,
    private val onDelete: (ShortcutEntry) -> Unit,
) : RecyclerView.Adapter<ShortcutAdapter.ShortcutViewHolder>() {
    private val items = mutableListOf<ShortcutEntry>()

    fun submitList(newItems: List<ShortcutEntry>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.shortcut.lowercase() })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ShortcutEntry) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
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
}
