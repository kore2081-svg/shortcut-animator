package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutEntryBinding

class ShortcutEntryAdapter(
    private val onShortcutClick: (ShortcutEntry) -> Unit,
    private val onShortcutDelete: (ShortcutEntry) -> Unit,
) : RecyclerView.Adapter<ShortcutEntryAdapter.ShortcutViewHolder>() {
    private val items = mutableListOf<ShortcutEntry>()

    fun submitList(newItems: List<ShortcutEntry>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.shortcut.lowercase() })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ShortcutEntry, position: Int) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.orderText.text = "${position + 1}."
            binding.orderText.setTextColor(theme.textPrimary)
            binding.shortcutText.text = item.shortcut
            binding.shortcutText.setTextColor(theme.textPrimary)
            binding.expandsToText.text = item.expandsTo
            binding.expandsToText.setTextColor(theme.textSecondary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.openArrowText.setTextColor(theme.accentColor)
            binding.openArrowText.setOnClickListener { onShortcutClick(item) }
            binding.root.setOnLongClickListener {
                onShortcutDelete(item)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(items[position], position)
    }
}
