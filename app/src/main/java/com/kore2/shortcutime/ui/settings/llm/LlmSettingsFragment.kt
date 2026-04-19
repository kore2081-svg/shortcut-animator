package com.kore2.shortcutime.ui.settings.llm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.databinding.FragmentLlmSettingsBinding
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.launch

class LlmSettingsFragment : Fragment() {
    private var _binding: FragmentLlmSettingsBinding? = null
    private val binding get() = _binding!!

    private val vm: LlmSettingsViewModel by viewModels { LlmSettingsViewModel.factory }
    private lateinit var rowAdapter: ProviderRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLlmSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        rowAdapter = ProviderRowAdapter { provider ->
            ApiKeyDialogFragment.newInstance(provider).show(parentFragmentManager, "api_key_dialog")
        }
        binding.providersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.providersRecyclerView.adapter = rowAdapter

        binding.dailyCapSeekBar.max = LlmSettingsStore.MAX_CAP - LlmSettingsStore.MIN_CAP
        binding.dailyCapSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) vm.setDailyCap(progress + LlmSettingsStore.MIN_CAP)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: LlmSettingsViewModel.State) {
        rowAdapter.submit(
            ProviderId.values().map { ProviderRowAdapter.Row(it, saved = it in state.savedProviders) }
        )
        renderActiveProviderGroup(state)
        renderModelSpinner(state)
        val cap = state.settings.dailyCallCap
        binding.dailyCapValue.text = getString(R.string.llm_daily_cap_format, cap)
        binding.dailyCapSeekBar.progress = cap - LlmSettingsStore.MIN_CAP
        binding.todayUsage.text = getString(
            R.string.llm_today_usage_format,
            state.settings.todayCallCount, cap,
        )
    }

    private fun renderActiveProviderGroup(state: LlmSettingsViewModel.State) {
        binding.activeProviderGroup.removeAllViews()
        val saved = state.savedProviders.toList().sortedBy { it.ordinal }
        if (saved.isEmpty()) {
            binding.noKeysHint.visibility = View.VISIBLE
            binding.activeProviderGroup.visibility = View.GONE
            return
        }
        binding.noKeysHint.visibility = View.GONE
        binding.activeProviderGroup.visibility = View.VISIBLE
        saved.forEach { provider ->
            val rb = RadioButton(requireContext()).apply {
                text = getString(providerLabelRes(provider))
                isChecked = state.settings.activeProvider == provider
                setOnClickListener { vm.setActiveProvider(provider) }
            }
            binding.activeProviderGroup.addView(rb)
        }
    }

    private fun renderModelSpinner(state: LlmSettingsViewModel.State) {
        val active = state.settings.activeProvider
        if (active == null) {
            binding.modelSpinner.adapter = null
            return
        }
        val models = vm.modelsFor(active)
        val labels = models.map { model ->
            if (model.isRecommended) "${model.displayName} (${getString(R.string.llm_recommended_badge)})"
            else model.displayName
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.modelSpinner.adapter = adapter
        val currentId = vm.currentModelFor(active)
        val idx = models.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        binding.modelSpinner.setSelection(idx)
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = models[position]
                if (selected.id != currentId) vm.setModel(active, selected.id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun providerLabelRes(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }
}
