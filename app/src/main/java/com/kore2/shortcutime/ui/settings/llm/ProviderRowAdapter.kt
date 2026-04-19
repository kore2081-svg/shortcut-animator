package com.kore2.shortcutime.ui.settings.llm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.R
import com.kore2.shortcutime.databinding.ItemProviderRowBinding
import com.kore2.shortcutime.llm.ProviderId

class ProviderRowAdapter(
    private val onClick: (ProviderId) -> Unit,
) : RecyclerView.Adapter<ProviderRowAdapter.VH>() {

    data class Row(val providerId: ProviderId, val saved: Boolean)

    private var rows: List<Row> = emptyList()

    fun submit(rows: List<Row>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProviderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

    override fun getItemCount(): Int = rows.size

    inner class VH(private val binding: ItemProviderRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Row) {
            val ctx = binding.root.context
            binding.providerName.text = ctx.getString(providerLabel(row.providerId))
            binding.providerStatus.text = ctx.getString(
                if (row.saved) R.string.llm_key_status_saved else R.string.llm_key_status_missing
            )
            binding.root.setOnClickListener { onClick(row.providerId) }
        }
    }

    private fun providerLabel(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }
}
