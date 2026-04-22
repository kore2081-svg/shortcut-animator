package com.kore2.shortcutime.ui.editor

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.DialogExampleEditorBinding
import com.kore2.shortcutime.databinding.FragmentShortcutEditorBinding
import com.kore2.shortcutime.ui.AutoExampleAdapter
import com.kore2.shortcutime.ui.ManualExampleAdapter
import com.kore2.shortcutime.ui.applyBodyTextTheme
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyInputLayoutTheme
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

    private lateinit var manualAdapter: ManualExampleAdapter
    private lateinit var autoAdapter: AutoExampleAdapter
    private lateinit var manualTouchHelper: ItemTouchHelper
    private lateinit var autoTouchHelper: ItemTouchHelper

    private var selectedGenerateCount: Int = 1
    private var manualSectionExpanded = true

    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    // ── CSV export ────────────────────────────────────────────────────────────
    private var csvShortcutText: String = ""
    private var csvExpandsToText: String = ""

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        val examples = viewModel.workingExamples.value
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                // UTF-8 BOM — Excel이 한글을 올바르게 열도록
                os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                os.write(buildCsvContent(csvShortcutText, csvExpandsToText, examples).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(requireContext(), R.string.toast_csv_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.toast_csv_error, Toast.LENGTH_SHORT).show()
        }
    }

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

        // Manual examples adapter + ItemTouchHelper
        manualAdapter = ManualExampleAdapter(
            onEdit = { openExampleDialog(it) },
            onDelete = { viewModel.deleteExample(it.id) },
            onTranslate = { viewModel.translateExample(it) },
            onStartDrag = { holder -> manualTouchHelper.startDrag(holder) },
        )
        manualTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                manualAdapter.moveItem(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                viewModel.setManualExamples(manualAdapter.getItems())
            }
        })

        // Auto examples adapter + ItemTouchHelper
        autoAdapter = AutoExampleAdapter(
            onDelete = { viewModel.deleteExample(it.id) },
            onTranslate = { viewModel.translateExample(it) },
            onStartDrag = { holder -> autoTouchHelper.startDrag(holder) },
        )
        autoTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                autoAdapter.moveItem(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                viewModel.setAutoExamples(autoAdapter.getItems())
            }
        })

        binding.manualExamplesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.manualExamplesRecyclerView.adapter = manualAdapter
        manualTouchHelper.attachToRecyclerView(binding.manualExamplesRecyclerView)

        binding.autoExamplesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.autoExamplesRecyclerView.adapter = autoAdapter
        autoTouchHelper.attachToRecyclerView(binding.autoExamplesRecyclerView)

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.previewTitle.text = getString(R.string.preview_title)
        binding.colemakPreview.setOnAnimationFinishedListener {
            binding.previewStatus.text = getString(R.string.preview_complete, it)
        }

        // Manual section collapse toggle
        binding.manualCollapseToggle.setOnClickListener {
            manualSectionExpanded = !manualSectionExpanded
            binding.manualExamplesRecyclerView.visibility =
                if (manualSectionExpanded) View.VISIBLE else View.GONE
            binding.manualCollapseToggle.text = if (manualSectionExpanded) "▽" else "△"
        }

        setupGenerateCountButtons()
        binding.addExampleButton.setOnClickListener { openExampleDialog(null) }
        binding.generateExamplesButton.setOnClickListener {
            val em = ShortcutApplication.from(requireContext()).entitlementManager
            if (!em.isPro() && em.getMonthlyAiUsage() >= BillingConstants.FREE_AI_MONTHLY_CAP) {
                showLimitDialog(LimitReason.AI)
                return@setOnClickListener
            }
            val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
            val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
            viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
        }
        binding.saveButton.setOnClickListener { onSaveClick() }
        binding.exportCsvButton.setOnClickListener { onExportCsvClick() }
        binding.shortcutInput.addTextChangedListener { text ->
            schedulePreview(text?.toString().orEmpty())
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
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
                        val manuals = examples.filter { it.sourceType == ExampleSourceType.MANUAL }
                        val autos = examples.filter { it.sourceType == ExampleSourceType.AUTO }
                        manualAdapter.submitList(manuals)
                        autoAdapter.submitList(autos)
                        binding.exampleCountText.text =
                            getString(R.string.example_count_format, examples.size)
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
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        val buttons = listOf(
            binding.generateOneButton to 1,
            binding.generateThreeButton to 3,
            binding.generateFiveButton to 5,
        )
        buttons.forEach { (button, count) ->
            button.isChecked = count == selectedGenerateCount
            val bgColor = if (count == selectedGenerateCount) theme.accentColor else theme.keyBackground
            button.backgroundTintList = ColorStateList.valueOf(bgColor)
            button.strokeColor = ColorStateList.valueOf(theme.strokeColor)
            button.setTextColor(if (count == selectedGenerateCount) theme.appBackground else theme.textPrimary)
        }
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.shortcutInputLayout, binding.shortcutInput, theme)
        applyInputLayoutTheme(binding.expandsToInputLayout, binding.expandsToInput, theme)
        binding.previewCard.background =
            roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.previewCard)
        binding.previewTitle.setTextColor(theme.textPrimary)
        binding.previewStatus.setTextColor(theme.textSecondary)
        binding.helperText.setTextColor(theme.textSecondary)
        binding.colemakPreview.applyTheme(theme)
        applyBodyTextTheme(binding.examplesTitle, theme, emphasize = true)
        applyBodyTextTheme(binding.exampleCountText, theme)
        binding.manualCollapseToggle.setTextColor(theme.textSecondary)
        applyFilledButtonTheme(binding.addExampleButton, theme)
        applyFilledButtonTheme(binding.generateExamplesButton, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
        applyFabTheme(binding.exportCsvButton, theme)
        updateGenerateSelection()
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
        val result = viewModel.save(
            shortcut = shortcut,
            expandsTo = expandsTo,
            note = "",
            caseSensitive = false,
            backspaceToUndo = false,
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
            ShortcutEditorViewModel.SaveResult.Error -> {
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, R.string.snack_save_error, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showEventSnackbar(event: ShortcutEditorViewModel.EditorEvent) {
        val root = binding.root
        val msg: String
        val actionLabel: Int?
        val actionHandler: (() -> Unit)?
        when (event) {
            is ShortcutEditorViewModel.EditorEvent.GenerateSuccess -> {
                // Increment monthly AI usage only for free users, only on actual success
                val em = ShortcutApplication.from(requireContext()).entitlementManager
                if (!em.isPro()) em.incrementMonthlyAiUsage()
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
            ShortcutEditorViewModel.EditorEvent.ExampleCapReached -> {
                msg = getString(R.string.snack_example_cap_reached, ShortcutEditorViewModel.MAX_EXAMPLES_PER_SHORTCUT)
                actionLabel = null; actionHandler = null
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

    // ── CSV export helpers ────────────────────────────────────────────────────

    private fun onExportCsvClick() {
        val em = ShortcutApplication.from(requireContext()).entitlementManager
        if (!em.isPro()) {
            showLimitDialog(LimitReason.CSV)
            return
        }
        val examples = viewModel.workingExamples.value
        if (examples.isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_no_examples, Toast.LENGTH_SHORT).show()
            return
        }
        csvShortcutText = binding.shortcutInput.text?.toString().orEmpty().trim()
        csvExpandsToText = binding.expandsToInput.text?.toString().orEmpty().trim()
        val fileName = if (csvShortcutText.isNotBlank()) "${csvShortcutText}_examples.csv" else "examples.csv"
        createCsvLauncher.launch(fileName)
    }

    private fun buildCsvContent(
        shortcut: String,
        expandsTo: String,
        examples: List<ExampleItem>,
    ): String = buildString {
        append("# Shortcut: $shortcut\r\n")
        append("# Expands To: $expandsTo\r\n")
        append("# Total examples: ${examples.size}\r\n")
        append("\r\n")
        append("type,example_en,example_ko\r\n")
        examples.forEach { ex ->
            append("${ex.sourceType.name},")
            append("${csvCell(ex.english)},")
            append("${csvCell(ex.korean)}\r\n")
        }
    }

    /** Wraps [value] in double-quotes and escapes any internal double-quotes. */
    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 800L
    }
}
