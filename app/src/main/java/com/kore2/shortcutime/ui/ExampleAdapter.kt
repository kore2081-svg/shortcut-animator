package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.databinding.ItemExampleBinding

class ExampleAdapter(
    private val onEdit: (ExampleItem) -> Unit,
    private val onDelete: (ExampleItem) -> Unit,
    private val onTranslate: (ExampleItem) -> Unit = {},
) : RecyclerView.Adapter<ExampleAdapter.ExampleViewHolder>() {
    private val items = mutableListOf<ExampleItem>()

    fun submitList(newItems: List<ExampleItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExampleViewHolder {
        val binding = ItemExampleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExampleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExampleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ExampleViewHolder(
        private val binding: ItemExampleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExampleItem) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 16f, binding.root)
            binding.englishText.text = item.english
            binding.englishText.setTextColor(theme.textPrimary)
            binding.koreanText.text = item.korean
            binding.koreanText.setTextColor(theme.textSecondary)
            binding.sourceTypeText.text = item.sourceType.name
            binding.sourceTypeText.setTextColor(theme.accentColor)
            binding.editButton.setTextColor(theme.accentColor)
            binding.deleteButton.setTextColor(theme.textSecondary)
            binding.translateButton.setTextColor(theme.accentColor)
            binding.editButton.setOnClickListener { onEdit(item) }
            binding.deleteButton.setOnClickListener { onDelete(item) }
            binding.translateButton.setOnClickListener { onTranslate(item) }
        }
    }
}
