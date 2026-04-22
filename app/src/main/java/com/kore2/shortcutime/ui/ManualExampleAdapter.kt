package com.kore2.shortcutime.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ItemExampleManualBinding

class ManualExampleAdapter(
    private val onEdit: (ExampleItem) -> Unit,
    private val onDelete: (ExampleItem) -> Unit,
    private val onTranslate: (ExampleItem) -> Unit = {},
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
) : RecyclerView.Adapter<ManualExampleAdapter.ViewHolder>() {

    private val items = mutableListOf<ExampleItem>()

    fun submitList(newItems: List<ExampleItem>) {
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
        val binding = ItemExampleManualBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemExampleManualBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: ExampleItem) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 16f, binding.root)

            binding.dragHandle.setTextColor(theme.textSecondary)
            binding.englishText.text = item.english.ifBlank { item.korean }
            binding.englishText.setTextColor(theme.textPrimary)

            binding.koreanText.text = item.korean
            binding.koreanText.visibility = if (item.korean.isNotBlank()) View.VISIBLE else View.GONE
            binding.koreanText.setTextColor(theme.textSecondary)

            binding.editButton.setTextColor(theme.accentColor)
            binding.deleteButton.setTextColor(theme.textSecondary)
            binding.translateButton.setTextColor(theme.accentColor)

            binding.editButton.setOnClickListener { onEdit(item) }
            binding.deleteButton.setOnClickListener { onDelete(item) }
            binding.translateButton.setOnClickListener { onTranslate(item) }

            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
    }
}
