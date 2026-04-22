// app/src/main/java/com/kore2/shortcutime/billing/BillingUiHelper.kt
package com.kore2.shortcutime.billing

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R

enum class LimitReason { FOLDER, SHORTCUT, AI, CSV }

/**
 * Shows a limit-reached dialog with a "Pro 보기" button that navigates to ProUpgradeFragment.
 * Call this from any Fragment when a Free tier limit is hit.
 */
fun Fragment.showLimitDialog(reason: LimitReason) {
    val (title, message) = when (reason) {
        LimitReason.FOLDER -> Pair(
            getString(R.string.limit_folder_title),
            getString(R.string.limit_folder_message)
        )
        LimitReason.SHORTCUT -> Pair(
            getString(R.string.limit_shortcut_title),
            getString(R.string.limit_shortcut_message)
        )
        LimitReason.AI -> Pair(
            getString(R.string.limit_ai_title),
            getString(R.string.limit_ai_message)
        )
        LimitReason.CSV -> Pair(
            getString(R.string.limit_csv_title),
            getString(R.string.limit_csv_message)
        )
    }
    AlertDialog.Builder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.pro_see_upgrade) { _, _ ->
            findNavController().navigate(R.id.action_global_to_proUpgrade)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
