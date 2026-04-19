package com.kore2.shortcutime.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.FragmentSettingsBinding
import com.kore2.shortcutime.ui.applyToolbarTheme

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.aiSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_llmSettings)
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.placeholderText.setTextColor(theme.textSecondary)
    }
}
