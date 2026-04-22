package com.kore2.shortcutime.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.databinding.FragmentFolderListBinding
import com.kore2.shortcutime.ui.FolderAdapter
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class FolderListFragment : Fragment() {
    private var _binding: FragmentFolderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FolderListViewModel by viewModels {
        FolderListViewModel.Factory(ShortcutApplication.from(requireContext()))
    }

    private lateinit var adapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFolderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FolderAdapter(
            onFolderClick = { openFolderDetail(it) },
            onFolderEdit = { openFolderEditor(it.id) },
            onFolderDelete = { confirmDelete(it) },
        )
        binding.folderRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.folderRecyclerView.adapter = adapter

        binding.addFolderFab.setOnClickListener {
            val em = ShortcutApplication.from(requireContext()).entitlementManager
            if (!em.isPro() && viewModel.folders.value.size >= BillingConstants.FREE_MAX_FOLDERS) {
                showLimitDialog(LimitReason.FOLDER)
            } else {
                openFolderEditor(null)
            }
        }
        binding.settingsFab.setOnClickListener { openSettings() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.folders.collect { list ->
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.emptyStateText.visibility = if (empty) View.VISIBLE else View.GONE
                    if (empty) {
                        binding.emptyStateText.text = getString(R.string.empty_folders)
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

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addFolderFab, theme)
        applyFabTheme(binding.settingsFab, theme)
    }

    private fun openFolderDetail(folder: FolderItem) {
        val action = FolderListFragmentDirections
            .actionFolderListToFolderDetail(folder.id)
        findNavController().navigate(action)
    }

    private fun openFolderEditor(folderId: String?) {
        val action = FolderListFragmentDirections
            .actionFolderListToFolderEditor(folderId)
        findNavController().navigate(action)
    }

    private fun openSettings() {
        findNavController().navigate(R.id.action_folderList_to_settings)
    }

    private fun confirmDelete(folder: FolderItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_folder_title))
            .setMessage(getString(R.string.dialog_delete_folder_message, folder.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteFolder(folder.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
