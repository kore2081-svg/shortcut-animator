package com.kore2.shortcutime.ui

import android.text.SpannableString
import android.text.Spannable
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ItemExampleAutoBinding

class AutoExampleAdapter(
    private val onDelete: (ExampleItem) -> Unit,
    private val onTranslate: (ExampleItem) -> Unit = {},
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
    private val getShortcut: () -> String = { "" },
    private val getExpandsTo: () -> String = { "" },
) : RecyclerView.Adapter<AutoExampleAdapter.ViewHolder>() {

    private val items = mutableListOf<ExampleItem>()
    private val collapsed = mutableSetOf<String>()
    private val shortMode = mutableSetOf<String>()
    private val translationVisible = mutableSetOf<String>()

    fun submitList(newItems: List<ExampleItem>) {
        val newIds = newItems.map { it.id }.toSet()
        collapsed.retainAll(newIds)
        shortMode.retainAll(newIds)
        translationVisible.retainAll(newIds)
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
    }

    fun getItems(): List<ExampleItem> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExampleAutoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemExampleAutoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExampleItem) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 16f, binding.root)

            val isCollapsed = item.id in collapsed
            val isShort = item.id in shortMode
            val isTranslVisible = item.id in translationVisible

            binding.dragHandle.setTextColor(theme.textSecondary)

            // English text in header — maxLines + ellipsize change with collapse state
            val displayText = if (isShort) buildShortText(item, theme) else SpannableString(item.english.ifBlank { item.korean })
            binding.englishText.text = displayText
            binding.englishText.setTextColor(theme.textPrimary)
            if (isCollapsed) {
                binding.englishText.maxLines = 1
                binding.englishText.ellipsize = TextUtils.TruncateAt.END
            } else {
                binding.englishText.maxLines = Int.MAX_VALUE
                binding.englishText.ellipsize = null
            }

            // Collapse toggle
            binding.contentContainer.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            binding.collapseToggle.text = if (isCollapsed) "▶" else "▽"
            binding.collapseToggle.setTextColor(theme.textSecondary)

            // Short mode button
            binding.shortToggleButton.text = if (isShort) "expand" else "short"
            binding.shortToggleButton.setTextColor(theme.accentColor)

            // Korean text (inside content container, toggled by 번역)
            binding.koreanText.text = item.korean
            binding.koreanText.visibility = if (isTranslVisible && item.korean.isNotBlank()) View.VISIBLE else View.GONE
            binding.koreanText.setTextColor(theme.textSecondary)

            // Translate button text
            binding.translateButton.text = if (isTranslVisible) "번역 on" else "번역"
            binding.translateButton.setTextColor(theme.accentColor)

            binding.deleteButton.setTextColor(theme.textSecondary)

            // Click listeners
            binding.collapseToggle.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (item.id in collapsed) collapsed.remove(item.id) else collapsed.add(item.id)
                notifyItemChanged(pos)
            }

            binding.shortToggleButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (item.id in shortMode) shortMode.remove(item.id) else shortMode.add(item.id)
                notifyItemChanged(pos)
            }

            binding.translateButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (isTranslVisible) {
                    translationVisible.remove(item.id)
                    notifyItemChanged(pos)
                } else {
                    translationVisible.add(item.id)
                    if (item.korean.isBlank()) {
                        onTranslate(item) // async — Korean will show when submitList brings the updated item
                    }
                    notifyItemChanged(pos)
                }
            }

            binding.deleteButton.setOnClickListener { onDelete(item) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }

        private fun buildShortText(item: ExampleItem, theme: com.kore2.shortcutime.data.KeyboardThemePalette): SpannableString {
            val shortcut = getShortcut().trim()
            val expandsTo = getExpandsTo().trim()
            val original = item.english.ifBlank { item.korean }

            val result = if (shortcut.isNotBlank() && expandsTo.isNotBlank() &&
                original.contains(expandsTo, ignoreCase = true)
            ) {
                original.replace(expandsTo, shortcut, ignoreCase = true)
            } else if (shortcut.isNotBlank()) {
                "[$shortcut] $original"
            } else {
                original
            }

            val spannable = SpannableString(result)
            val start = result.indexOf(shortcut)
            if (shortcut.isNotBlank() && start >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(theme.accentColor),
                    start,
                    start + shortcut.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return spannable
        }
    }
}
