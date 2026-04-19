package com.kore2.shortcutime.ui.settings.llm

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.DialogApiKeyBinding
import com.kore2.shortcutime.llm.LlmError
import com.kore2.shortcutime.llm.LlmException
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiKeyDialogFragment : DialogFragment() {

    private val vm: LlmSettingsViewModel by activityViewModels { LlmSettingsViewModel.factory }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val provider = ProviderId.valueOf(requireArguments().getString(ARG_PROVIDER)!!)
        val binding = DialogApiKeyBinding.inflate(LayoutInflater.from(requireContext()))
        val providerLabel = getString(providerLabelRes(provider))
        binding.getKeyLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(providerKeyUrl(provider))))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.api_key_dialog_title_format, providerLabel))
            .setView(binding.root)
            .setPositiveButton(R.string.api_key_action_test_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.api_key_action_delete, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            positive.setOnClickListener {
                val key = binding.keyInput.text?.toString().orEmpty().trim()
                if (key.isEmpty()) {
                    binding.statusLine.text = getString(R.string.snack_key_invalid)
                    return@setOnClickListener
                }
                positive.isEnabled = false
                binding.statusLine.text = getString(R.string.api_key_status_validating)
                lifecycleScope.launch {
                    val app = ShortcutApplication.from(requireContext())
                    val adapter = app.llmRegistry.adapterFor(provider)
                    val result = withContext(Dispatchers.IO) { adapter.validateKey(key) }
                    positive.isEnabled = true
                    if (result.isSuccess) {
                        vm.saveApiKey(provider, key)
                        dialog.dismiss()
                    } else {
                        val err = (result.exceptionOrNull() as? LlmException)?.error
                        binding.statusLine.text = errorMessage(err)
                    }
                }
            }
            neutral.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.api_key_confirm_delete_title)
                    .setMessage(getString(R.string.api_key_confirm_delete_message, providerLabel))
                    .setPositiveButton(R.string.api_key_action_delete) { _, _ ->
                        vm.deleteApiKey(provider)
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return dialog
    }

    private fun errorMessage(err: LlmError?): String = when (err) {
        LlmError.InvalidKey -> getString(R.string.snack_key_invalid)
        LlmError.Network -> getString(R.string.snack_network)
        LlmError.Timeout -> getString(R.string.snack_timeout)
        LlmError.ServerError -> getString(R.string.snack_server_error)
        is LlmError.Unknown -> getString(R.string.snack_unknown_format, err.message)
        else -> getString(R.string.snack_unknown_format, err?.toString().orEmpty())
    }

    private fun providerLabelRes(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }

    private fun providerKeyUrl(id: ProviderId): String = when (id) {
        ProviderId.OPENAI -> "https://platform.openai.com/api-keys"
        ProviderId.CLAUDE -> "https://console.anthropic.com/settings/keys"
        ProviderId.GEMINI -> "https://aistudio.google.com/app/apikey"
        ProviderId.GROK -> "https://console.x.ai/"
        ProviderId.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
    }

    companion object {
        private const val ARG_PROVIDER = "arg_provider"

        fun newInstance(provider: ProviderId) = ApiKeyDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_PROVIDER, provider.name) }
        }
    }
}
