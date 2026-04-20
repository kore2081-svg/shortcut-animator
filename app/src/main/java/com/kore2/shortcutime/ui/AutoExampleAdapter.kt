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
import com.kore2.shortcutime.data.KeyboardThemePalette
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

    // ---------------------------------------------------------------------------
    // Short-mode text building
    // ---------------------------------------------------------------------------

    /**
     * Finds the character range [start, end) in [original] that best matches [expandsTo].
     *
     * Strategy:
     * 1. Exact case-insensitive match (handles normal cases and newly generated examples).
     * 2. Word-sequence sliding-window match with a 65% threshold.
     *    This handles LLM paraphrasing where contractions get expanded
     *    (e.g., "would've" → "would have") — the surrounding words still match.
     */
    private fun findReplacementRange(original: String, expandsTo: String): Pair<Int, Int>? {
        // 1. Exact match
        val exactIdx = original.indexOf(expandsTo, ignoreCase = true)
        if (exactIdx >= 0) return exactIdx to exactIdx + expandsTo.length

        // 2. Word-sequence sliding window
        val wordRe = Regex("\\b\\w+\\b")
        val expandWords = wordRe.findAll(expandsTo).map { it.value.lowercase() }.toList()
        if (expandWords.size < 2) return null

        val origTokens = wordRe.findAll(original).toList()
        val origWords = origTokens.map { it.value.lowercase() }

        // Minimum consecutive matching words required (at least 65% of expandsTo words, min 2)
        val minMatch = maxOf(2, (expandWords.size * 0.65).toInt())

        var bestStart = -1
        var bestEnd = -1
        var bestScore = 0

        for (i in origWords.indices) {
            val window = minOf(expandWords.size, origWords.size - i)
            if (window < minMatch) break

            var score = 0
            var lastMatchedJ = -1
            for (j in 0 until window) {
                if (expandWords[j] == origWords[i + j]) {
                    score++
                    lastMatchedJ = j
                }
            }

            if (score >= minMatch && score > bestScore) {
                bestScore = score
                bestStart = origTokens[i].range.first
                bestEnd = origTokens[i + lastMatchedJ].range.last + 1
            }
        }

        return if (bestStart >= 0) bestStart to bestEnd else null
    }

    private fun buildShortText(item: ExampleItem, theme: KeyboardThemePalette): SpannableString {
        val shortcut = getShortcut().trim()
        val expandsTo = getExpandsTo().trim()
        val original = item.english.ifBlank { item.korean }

        if (shortcut.isBlank()) return SpannableString(original)

        val range = if (expandsTo.isNotBlank()) findReplacementRange(original, expandsTo) else null

        val result: String
        val hlStart: Int
        val hlEnd: Int

        if (range != null) {
            result = original.substring(0, range.first) + shortcut + original.substring(range.second)
            hlStart = range.first
            hlEnd = range.first + shortcut.length
        } else {
            // Fallback: prepend [shortcut] to signal no match found
            result = "[$shortcut] $original"
            hlStart = 1
            hlEnd = 1 + shortcut.length
        }

        val spannable = SpannableString(result)
        spannable.setSpan(
            ForegroundColorSpan(theme.accentColor),
            hlStart.coerceIn(0, result.length),
            hlEnd.coerceIn(0, result.length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return spannable
    }

    // ---------------------------------------------------------------------------
    // ViewHolder
    // ---------------------------------------------------------------------------

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

            // English text in header: full or truncated depending on collapse state
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
