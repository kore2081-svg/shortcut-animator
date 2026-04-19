package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutEntryBinding

class ShortcutEntryAdapter(
    private val onShortcutClick: (ShortcutEntry) -> Unit,
    private val onShortcutDelete: (ShortcutEntry) -> Unit,
) : ListAdapter<ShortcutEntry, ShortcutEntryAdapter.ShortcutViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun submitList(list: List<ShortcutEntry>?) {
        super.submitList(list?.sortedBy { it.shortcut.lowercase() })
    }

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore = ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: ShortcutEntry, position: Int) {
            val theme = themeStore.currentTheme()
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

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ShortcutEntry>() {
            override fun areItemsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old.id == new.id
            override fun areContentsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old == new
        }
    }
}
