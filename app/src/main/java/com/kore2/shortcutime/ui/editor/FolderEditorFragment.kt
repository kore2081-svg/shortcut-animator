package com.kore2.shortcutime.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.FragmentFolderEditorBinding
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyInputLayoutTheme
import com.kore2.shortcutime.ui.applyToolbarTheme

class FolderEditorFragment : Fragment() {
    private var _binding: FragmentFolderEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FolderEditorViewModel by viewModels { FolderEditorViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFolderEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewModel.existing?.let {
            binding.topToolbar.title = getString(R.string.title_edit_folder)
            binding.titleInput.setText(it.title)
            binding.noteInput.setText(it.note)
        } ?: run {
            binding.topToolbar.title = getString(R.string.title_add_folder)
        }

        binding.saveButton.setOnClickListener { onSaveClick() }
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
        applyInputLayoutTheme(binding.titleInputLayout, binding.titleInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
    }

    private fun onSaveClick() {
        val title = binding.titleInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()
        if (!viewModel.save(title, note)) {
            binding.titleInputLayout.error = getString(R.string.error_folder_title_required)
            return
        }
        binding.titleInputLayout.error = null
        findNavController().popBackStack()
    }
}
