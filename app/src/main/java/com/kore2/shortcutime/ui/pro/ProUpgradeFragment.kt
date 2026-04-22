package com.kore2.shortcutime.ui.pro

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.billing.EntitlementManager
import com.kore2.shortcutime.databinding.FragmentProUpgradeBinding
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class ProUpgradeFragment : Fragment() {

    private var _binding: FragmentProUpgradeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProUpgradeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topToolbar.title = getString(R.string.pro_upgrade_title)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val entitlement = ShortcutApplication.from(requireContext()).entitlementManager

        if (entitlement.isPro()) showAlreadyPro()

        binding.purchaseButton.setOnClickListener {
            lifecycleScope.launch { handlePurchase(entitlement) }
        }

        binding.restoreButton.setOnClickListener {
            lifecycleScope.launch { handleRestore(entitlement) }
        }
        // Note: comparison table and theme are applied in onResume() → applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Purchase flow ─────────────────────────────────────────────────────

    private suspend fun handlePurchase(entitlement: EntitlementManager) {
        binding.purchaseButton.isEnabled = false
        when (val result = entitlement.purchase(requireActivity())) {
            EntitlementManager.PurchaseResult.Success -> {
                Toast.makeText(requireContext(), R.string.toast_purchase_success, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            EntitlementManager.PurchaseResult.Cancelled -> {
                Toast.makeText(requireContext(), R.string.toast_purchase_cancelled, Toast.LENGTH_SHORT).show()
                binding.purchaseButton.isEnabled = true
            }
            is EntitlementManager.PurchaseResult.Error -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_purchase_error, result.message),
                    Toast.LENGTH_LONG,
                ).show()
                binding.purchaseButton.isEnabled = true
            }
        }
    }

    private suspend fun handleRestore(entitlement: EntitlementManager) {
        binding.restoreButton.isEnabled = false
        when (val result = entitlement.restorePurchases()) {
            EntitlementManager.RestoreResult.Restored -> {
                Toast.makeText(requireContext(), R.string.toast_restore_success, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            EntitlementManager.RestoreResult.NothingToRestore -> {
                Toast.makeText(requireContext(), R.string.toast_restore_nothing, Toast.LENGTH_SHORT).show()
                binding.restoreButton.isEnabled = true
            }
            is EntitlementManager.RestoreResult.Error -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_purchase_error, result.message),
                    Toast.LENGTH_LONG,
                ).show()
                binding.restoreButton.isEnabled = true
            }
        }
    }

    // ── Already Pro ───────────────────────────────────────────────────────

    private fun showAlreadyPro() {
        binding.purchaseButton.isEnabled = false
        binding.purchaseButton.alpha = 0.4f
        binding.alreadyProText.visibility = View.VISIBLE
    }

    // ── Comparison table (built dynamically to apply theme colors) ────────

    private fun buildComparisonTable() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        val container = binding.comparisonTable
        container.removeAllViews()

        val rows = listOf(
            Triple(R.string.pro_row_folders,   R.string.pro_free_folders,   R.string.pro_paid_folders),
            Triple(R.string.pro_row_shortcuts, R.string.pro_free_shortcuts, R.string.pro_paid_shortcuts),
            Triple(R.string.pro_row_ai,        R.string.pro_free_ai,        R.string.pro_paid_ai),
            Triple(R.string.pro_row_csv,       R.string.pro_free_csv,       R.string.pro_paid_csv),
            Triple(R.string.pro_row_themes,    R.string.pro_free_themes,    R.string.pro_paid_themes),
            Triple(R.string.pro_row_future,    R.string.pro_free_future,    R.string.pro_paid_future),
        )

        // Header row
        container.addView(buildRow(
            label = getString(R.string.pro_col_feature),
            freeVal = getString(R.string.pro_col_free),
            proVal = getString(R.string.pro_col_pro),
            isHeader = true,
            bgColor = theme.previewBackground,
            labelColor = theme.textSecondary,
            freeColor = theme.textSecondary,
            proColor = theme.accentColor,
        ))

        // Data rows
        rows.forEach { (featureRes, freeRes, proRes) ->
            container.addView(buildRow(
                label = getString(featureRes),
                freeVal = getString(freeRes),
                proVal = getString(proRes),
                isHeader = false,
                bgColor = theme.previewBackground,
                labelColor = theme.textPrimary,
                freeColor = theme.textSecondary,
                proColor = theme.accentColor,
            ))
        }
    }

    private fun buildRow(
        label: String,
        freeVal: String,
        proVal: String,
        isHeader: Boolean,
        bgColor: Int,
        labelColor: Int,
        freeColor: Int,
        proColor: Int,
    ): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        fun cell(text: String, color: Int, weight: Float, align: Int = Gravity.START): TextView {
            return TextView(requireContext()).apply {
                this.text = text
                textSize = if (isHeader) 11f else 12f
                setTextColor(color)
                if (isHeader) setTypeface(typeface, Typeface.BOLD)
                gravity = align or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                    val padH = (12 * density).toInt()
                    val padV = (10 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                }
            }
        }

        row.addView(cell(label, labelColor, 1.4f))
        row.addView(cell(freeVal, freeColor, 1f, Gravity.CENTER_HORIZONTAL))
        row.addView(cell(proVal, proColor, 1f, Gravity.CENTER_HORIZONTAL))
        return row
    }

    // ── Theme application ─────────────────────────────────────────────────

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.heroTitle.setTextColor(theme.accentColor)
        binding.heroSubtitle.setTextColor(theme.textSecondary)
        binding.priceLabel.setTextColor(theme.textSecondary)
        binding.priceText.setTextColor(theme.accentColor)
        binding.comparisonTable.setBackgroundColor(theme.previewBackground)
        binding.restoreButton.setTextColor(theme.textSecondary)
        applyFilledButtonTheme(binding.purchaseButton, theme)
        buildComparisonTable()
    }
}
