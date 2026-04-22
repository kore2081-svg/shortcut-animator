package com.kore2.shortcutime.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.FragmentFolderDetailBinding
import com.kore2.shortcutime.ui.ShortcutEntryAdapter
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class FolderDetailFragment : Fragment() {
    private var _binding: FragmentFolderDetailBinding? = null
    private val binding get() = _binding!!

    private val args: FolderDetailFragmentArgs by navArgs()

    private val viewModel: FolderDetailViewModel by viewModels {
        FolderDetailViewModel.factory(args.folderId)
    }

    private lateinit var adapter: ShortcutEntryAdapter

    // ── CSV export: opens system file-picker, no permission needed ──────────
    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        val state = viewModel.state.value as? FolderDetailState.Loaded ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                // UTF-8 BOM so Excel opens Korean characters correctly
                os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                os.write(buildCsvContent(state.folder).toByteArray(Charsets.UTF_8))
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
        _binding = FragmentFolderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShortcutEntryAdapter(
            onShortcutClick = { openShortcutEditor(it.id) },
            onShortcutDelete = { confirmDelete(it) },
        )
        binding.shortcutRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.shortcutRecyclerView.adapter = adapter

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.addShortcutFab.setOnClickListener {
            val em = ShortcutApplication.from(requireContext()).entitlementManager
            val state = viewModel.state.value as? FolderDetailState.Loaded
            val shortcutCount = state?.folder?.shortcuts?.size ?: 0
            if (!em.isPro() && shortcutCount >= BillingConstants.FREE_MAX_SHORTCUTS_PER_FOLDER) {
                showLimitDialog(LimitReason.SHORTCUT)
            } else {
                openShortcutEditor(null)
            }
        }

        binding.exportCsvFab.setOnClickListener {
            val em = ShortcutApplication.from(requireContext()).entitlementManager
            if (!em.isPro()) {
                showLimitDialog(LimitReason.CSV)
                return@setOnClickListener
            }
            val state = viewModel.state.value as? FolderDetailState.Loaded ?: return@setOnClickListener
            val safeName = state.folder.title
                .replace(Regex("[^\\w가-힣\\s-]"), "")
                .trim()
                .ifBlank { "shortcuts" }
            createCsvLauncher.launch("${safeName}_shortcuts.csv")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is FolderDetailState.Loaded -> renderFolder(state)
                        FolderDetailState.NotFound -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.folder_not_found,
                                Toast.LENGTH_SHORT,
                            ).show()
                            findNavController().popBackStack()
                        }
                        FolderDetailState.Loading -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderFolder(state: FolderDetailState.Loaded) {
        val folder = state.folder
        binding.topToolbar.title = folder.title
        binding.folderHeaderText.text =
            getString(R.string.folder_header_format, folder.title, folder.shortcuts.size)
        adapter.submitList(folder.shortcuts)
        val empty = folder.shortcuts.isEmpty()
        binding.emptyStateText.visibility = if (empty) View.VISIBLE else View.GONE
        binding.shortcutRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.folderHeaderText.setTextColor(theme.textSecondary)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addShortcutFab, theme)
        applyFabTheme(binding.exportCsvFab, theme)
    }

    private fun openShortcutEditor(shortcutId: String?) {
        val action = FolderDetailFragmentDirections
            .actionFolderDetailToShortcutEditor(args.folderId, shortcutId)
        findNavController().navigate(action)
    }

    private fun confirmDelete(shortcut: ShortcutEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, shortcut.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteShortcut(shortcut.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── CSV content builder ──────────────────────────────────────────────────
    private fun buildCsvContent(folder: FolderItem): String {
        return buildString {
            // Header metadata
            append("# Folder: ${folder.title}\r\n")
            append("# Total shortcuts: ${folder.shortcuts.size}\r\n")
            append("\r\n")
            // Column headers
            append("shortcut,expands_to,usage_count,example_count,examples_en,examples_ko\r\n")
            // Data rows
            folder.shortcuts.sortedBy { it.shortcut.lowercase() }.forEach { entry ->
                val examplesEn = entry.examples.joinToString(" | ") { it.english }
                val examplesKo = entry.examples.joinToString(" | ") { it.korean }
                append("${csvCell(entry.shortcut)},")
                append("${csvCell(entry.expandsTo)},")
                append("${entry.usageCount},")
                append("${entry.examples.size},")
                append("${csvCell(examplesEn)},")
                append("${csvCell(examplesKo)}\r\n")
            }
        }
    }

    /** Wraps [value] in double-quotes and escapes any internal double-quotes. */
    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
