package com.kore2.shortcutime.ui.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.DialogExampleEditorBinding
import com.kore2.shortcutime.databinding.FragmentShortcutEditorBinding
import com.kore2.shortcutime.ui.ExampleAdapter
import com.kore2.shortcutime.ui.ShortcutAdapter
import com.kore2.shortcutime.ui.applyBodyTextTheme
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyInputLayoutTheme
import com.kore2.shortcutime.ui.applySwitchTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import com.kore2.shortcutime.ui.roundedRectDrawable
import kotlinx.coroutines.launch
import java.util.UUID

class ShortcutEditorFragment : Fragment() {
    private var _binding: FragmentShortcutEditorBinding? = null
    private val binding get() = _binding!!

    private val args: ShortcutEditorFragmentArgs by navArgs()

    private val viewModel: ShortcutEditorViewModel by viewModels {
        ShortcutEditorViewModel.factory(args.folderId, args.shortcutId)
    }

    private lateinit var exampleAdapter: ExampleAdapter
    private lateinit var savedShortcutAdapter: ShortcutAdapter

    private var selectedGenerateCount: Int = 1
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShortcutEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exampleAdapter = ExampleAdapter(
            onEdit = { openExampleDialog(it) },
            onDelete = { viewModel.deleteExample(it.id) },
        )
        savedShortcutAdapter = ShortcutAdapter(
            onEdit = { openExistingShortcut(it.id) },
            onDelete = { confirmDeleteExisting(it) },
        )
        binding.examplesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.examplesRecyclerView.adapter = exampleAdapter
        binding.savedShortcutsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.savedShortcutsRecyclerView.adapter = savedShortcutAdapter

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.previewTitle.text = getString(R.string.preview_title)
        binding.colemakPreview.setOnAnimationFinishedListener {
            binding.previewStatus.text = getString(R.string.preview_complete, it)
        }

        setupGenerateCountButtons()
        binding.addExampleButton.setOnClickListener { openExampleDialog(null) }
        binding.generateExamplesButton.setOnClickListener {
            val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
            val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
            viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
        }
        binding.saveButton.setOnClickListener { onSaveClick() }
        binding.shortcutInput.addTextChangedListener { text ->
            schedulePreview(text?.toString().orEmpty())
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        viewModel.refreshSavedShortcuts()
    }

    override fun onDestroyView() {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        _binding = null
        super.onDestroyView()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.folderMissing.collect { missing ->
                        if (missing) {
                            Toast.makeText(
                                requireContext(),
                                R.string.folder_not_found,
                                Toast.LENGTH_SHORT,
                            ).show()
                            findNavController().popBackStack()
                        }
                    }
                }
                launch {
                    viewModel.entry.collect { entry ->
                        entry?.let { bindEntry(it) }
                        binding.topToolbar.title = if (entry != null) {
                            getString(R.string.title_edit_shortcut)
                        } else {
                            getString(R.string.title_add_shortcut_entry)
                        }
                    }
                }
                launch {
                    viewModel.workingExamples.collect { examples ->
                        exampleAdapter.submitList(examples)
                        binding.exampleCountText.text =
                            getString(R.string.example_count_format, examples.size)
                    }
                }
                launch {
                    viewModel.savedShortcuts.collect { list ->
                        savedShortcutAdapter.submitList(list)
                        binding.savedShortcutsRecyclerView.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.isGenerating.collect { generating ->
                        binding.generateExamplesButton.isEnabled = !generating
                        binding.generateExamplesButton.text = if (generating) {
                            getString(R.string.preview_animating, "")
                        } else {
                            getString(R.string.action_generate_examples)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { showEventSnackbar(it) }
                }
            }
        }
    }

    private fun bindEntry(entry: ShortcutEntry) {
        if (binding.shortcutInput.text?.toString() != entry.shortcut) {
            binding.shortcutInput.setText(entry.shortcut)
        }
        if (binding.expandsToInput.text?.toString() != entry.expandsTo) {
            binding.expandsToInput.setText(entry.expandsTo)
        }
        if (binding.noteInput.text?.toString() != entry.note) {
            binding.noteInput.setText(entry.note)
        }
        binding.caseSensitiveSwitch.isChecked = entry.caseSensitive
        binding.backspaceUndoSwitch.isChecked = entry.backspaceToUndo
    }

    private fun setupGenerateCountButtons() {
        binding.generateOneButton.setOnClickListener {
            selectedGenerateCount = 1
            updateGenerateSelection()
        }
        binding.generateThreeButton.setOnClickListener {
            selectedGenerateCount = 3
            updateGenerateSelection()
        }
        binding.generateFiveButton.setOnClickListener {
            selectedGenerateCount = 5
            updateGenerateSelection()
        }
        updateGenerateSelection()
    }

    private fun updateGenerateSelection() {
        val buttons = listOf(
            binding.generateOneButton to 1,
            binding.generateThreeButton to 3,
            binding.generateFiveButton to 5,
        )
        buttons.forEach { (button, count) ->
            button.isChecked = count == selectedGenerateCount
        }
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.shortcutInputLayout, binding.shortcutInput, theme)
        applyInputLayoutTheme(binding.expandsToInputLayout, binding.expandsToInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        binding.previewCard.background =
            roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.previewCard)
        binding.previewTitle.setTextColor(theme.textPrimary)
        binding.previewStatus.setTextColor(theme.textSecondary)
        binding.helperText.setTextColor(theme.textSecondary)
        binding.savedShortcutsTitle.setTextColor(theme.textPrimary)
        binding.colemakPreview.applyTheme(theme)
        applyBodyTextTheme(binding.examplesTitle, theme, emphasize = true)
        applyBodyTextTheme(binding.exampleCountText, theme)
        applyBodyTextTheme(binding.keywordSettingsTitle, theme, emphasize = true)
        applyFilledButtonTheme(binding.addExampleButton, theme)
        applyFilledButtonTheme(binding.generateOneButton, theme)
        applyFilledButtonTheme(binding.generateThreeButton, theme)
        applyFilledButtonTheme(binding.generateFiveButton, theme)
        applyFilledButtonTheme(binding.generateExamplesButton, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
        applySwitchTheme(binding.backspaceUndoSwitch, theme)
        applySwitchTheme(binding.caseSensitiveSwitch, theme)
    }

    private fun schedulePreview(raw: String) {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            binding.colemakPreview.clearAnimationState()
            binding.previewStatus.text = getString(R.string.preview_idle)
            return
        }
        binding.colemakPreview.setSequence(normalized)
        binding.previewStatus.text = getString(R.string.preview_typed, normalized)
        val next = Runnable {
            binding.colemakPreview.playSequence(normalized)
            binding.previewStatus.text = getString(R.string.preview_animating, normalized)
        }
        previewRunnable = next
        previewHandler.postDelayed(next, PREVIEW_DEBOUNCE_MS)
    }

    private fun openExampleDialog(existing: ExampleItem?) {
        val dialogBinding = DialogExampleEditorBinding.inflate(layoutInflater)
        dialogBinding.englishInput.setText(existing?.english.orEmpty())
        dialogBinding.koreanInput.setText(existing?.korean.orEmpty())
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        dialogBinding.root.setBackgroundColor(theme.appBackground)
        applyInputLayoutTheme(dialogBinding.englishInputLayout, dialogBinding.englishInput, theme)
        applyInputLayoutTheme(dialogBinding.koreanInputLayout, dialogBinding.koreanInput, theme)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.title_add_example else R.string.title_edit_example)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val english = dialogBinding.englishInput.text?.toString().orEmpty().trim()
                val korean = dialogBinding.koreanInput.text?.toString().orEmpty().trim()
                if (english.isBlank() && korean.isBlank()) return@setPositiveButton
                val updated = ExampleItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    english = english,
                    korean = korean,
                    sourceType = existing?.sourceType ?: ExampleSourceType.MANUAL,
                )
                viewModel.addOrUpdateExample(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onSaveClick() {
        val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
        val expandsTo = binding.expandsToInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()
        val result = viewModel.save(
            shortcut = shortcut,
            expandsTo = expandsTo,
            note = note,
            caseSensitive = binding.caseSensitiveSwitch.isChecked,
            backspaceToUndo = binding.backspaceUndoSwitch.isChecked,
        )
        when (result) {
            ShortcutEditorViewModel.SaveResult.MissingShortcut -> {
                binding.shortcutInputLayout.error = getString(R.string.error_shortcut_required)
            }
            ShortcutEditorViewModel.SaveResult.MissingExpandsTo -> {
                binding.shortcutInputLayout.error = null
                binding.expandsToInputLayout.error = getString(R.string.error_expands_to_required)
            }
            ShortcutEditorViewModel.SaveResult.Success -> {
                binding.shortcutInputLayout.error = null
                binding.expandsToInputLayout.error = null
                findNavController().popBackStack()
            }
        }
    }

    private fun openExistingShortcut(shortcutId: String) {
        if (viewModel.shortcutId == shortcutId) return
        val action = ShortcutEditorFragmentDirections
            .actionShortcutEditorSelf(viewModel.folderId, shortcutId)
        findNavController().navigate(action)
    }

    private fun confirmDeleteExisting(entry: ShortcutEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, entry.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteShortcut(entry.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEventSnackbar(event: ShortcutEditorViewModel.EditorEvent) {
        val root = binding.root
        val msg: String
        val actionLabel: Int?
        val actionHandler: (() -> Unit)?
        when (event) {
            is ShortcutEditorViewModel.EditorEvent.GenerateSuccess -> {
                msg = getString(R.string.snack_generate_success_format, event.addedCount); actionLabel = null; actionHandler = null
            }
            is ShortcutEditorViewModel.EditorEvent.GeneratePartial -> {
                msg = getString(R.string.snack_generate_partial_format, event.requested, event.got); actionLabel = null; actionHandler = null
            }
            ShortcutEditorViewModel.EditorEvent.NoActiveProvider -> {
                msg = getString(R.string.snack_no_provider)
                actionLabel = R.string.snack_action_settings
                actionHandler = { navigateToLlmSettings() }
            }
            ShortcutEditorViewModel.EditorEvent.NoKey -> {
                msg = getString(R.string.snack_no_key)
                actionLabel = R.string.snack_action_settings
                actionHandler = { navigateToLlmSettings() }
            }
            ShortcutEditorViewModel.EditorEvent.DailyCapExceeded -> {
                val cap = ShortcutApplication.from(requireContext()).llmSettingsStore.load().dailyCallCap
                msg = getString(R.string.snack_daily_cap_format, cap)
                actionLabel = R.string.snack_action_change_cap
                actionHandler = { navigateToLlmSettings() }
            }
            is ShortcutEditorViewModel.EditorEvent.GenerateError -> {
                msg = when (val err = event.error) {
                    com.kore2.shortcutime.llm.LlmError.Network -> getString(R.string.snack_network)
                    com.kore2.shortcutime.llm.LlmError.Timeout -> getString(R.string.snack_timeout)
                    com.kore2.shortcutime.llm.LlmError.InvalidKey -> getString(R.string.snack_generate_key_invalid)
                    com.kore2.shortcutime.llm.LlmError.RateLimited -> getString(R.string.snack_generate_rate_limited)
                    com.kore2.shortcutime.llm.LlmError.ServerError -> getString(R.string.snack_server_error)
                    com.kore2.shortcutime.llm.LlmError.ContentFiltered -> getString(R.string.snack_generate_content_filtered)
                    com.kore2.shortcutime.llm.LlmError.ParseFailure -> getString(R.string.snack_generate_parse_failure)
                    com.kore2.shortcutime.llm.LlmError.Truncated -> getString(R.string.snack_generate_truncated)
                    is com.kore2.shortcutime.llm.LlmError.Unknown -> getString(R.string.snack_unknown_format, err.message)
                }
                actionLabel = when (event.error) {
                    com.kore2.shortcutime.llm.LlmError.InvalidKey -> R.string.snack_action_settings
                    else -> R.string.snack_action_retry
                }
                actionHandler = {
                    if (event.error is com.kore2.shortcutime.llm.LlmError.InvalidKey) navigateToLlmSettings()
                    else retryLastGeneration()
                }
            }
        }
        val snack = com.google.android.material.snackbar.Snackbar.make(root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        if (actionLabel != null && actionHandler != null) snack.setAction(actionLabel) { actionHandler() }
        snack.show()
    }

    private fun navigateToLlmSettings() {
        findNavController().navigate(R.id.action_shortcutEditor_to_llmSettings)
    }

    private fun retryLastGeneration() {
        val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
        val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
        viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 800L
    }
}
