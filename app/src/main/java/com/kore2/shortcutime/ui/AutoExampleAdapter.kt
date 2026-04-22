package com.kore2.shortcutime.ui

import android.annotation.SuppressLint
import android.text.TextUtils
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
) : RecyclerView.Adapter<AutoExampleAdapter.ViewHolder>() {

    private val items = mutableListOf<ExampleItem>()
    private val collapsed = mutableSetOf<String>()
    private val translationVisible = mutableSetOf<String>()

    fun submitList(newItems: List<ExampleItem>) {
        val newIds = newItems.map { it.id }.toSet()
        collapsed.retainAll(newIds)
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

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: ExampleItem) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 16f, binding.root)

            val isCollapsed = item.id in collapsed
            val isTranslVisible = item.id in translationVisible

            binding.dragHandle.setTextColor(theme.textSecondary)

            // English text in header: full or truncated depending on collapse state
            binding.englishText.text = item.english.ifBlank { item.korean }
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

            // Korean text (inside content container, toggled by 번역)
            binding.koreanText.text = item.korean
            binding.koreanText.visibility = if (isTranslVisible && item.korean.isNotBlank()) View.VISIBLE else View.GONE
            binding.koreanText.setTextColor(theme.textSecondary)

            // Translate button
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

            binding.translateButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (isTranslVisible) {
                    translationVisible.remove(item.id)
                    notifyItemChanged(pos)
                } else {
                    translationVisible.add(item.id)
                    if (item.korean.isBlank()) {
                        onTranslate(item) // async — Korean will appear when submitList brings the updated item
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
    }
}
