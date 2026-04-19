package com.kore2.shortcutime.ui.settings.llm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.LlmSettings
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.llm.ModelCatalog
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LlmSettingsViewModel(
    private val keyStore: SecureKeyStore,
    private val settingsStore: LlmSettingsStore,
) : ViewModel() {

    data class State(
        val savedProviders: Set<ProviderId>,
        val settings: LlmSettings,
    )

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() { _state.value = buildState() }

    fun setActiveProvider(provider: ProviderId?) {
        settingsStore.setActiveProvider(provider)
        refresh()
    }

    fun setModel(provider: ProviderId, modelId: String) {
        settingsStore.setModel(provider, modelId)
        refresh()
    }

    fun setDailyCap(cap: Int) {
        settingsStore.setDailyCap(cap)
        refresh()
    }

    fun saveApiKey(provider: ProviderId, key: String) {
        keyStore.save(provider, key)
        refresh()
    }

    fun deleteApiKey(provider: ProviderId) {
        keyStore.clear(provider)
        val s = settingsStore.load()
        if (s.activeProvider == provider) settingsStore.setActiveProvider(null)
        refresh()
    }

    fun modelsFor(provider: ProviderId) = ModelCatalog.modelsFor(provider)
    fun currentModelFor(provider: ProviderId): String =
        settingsStore.load().modelByProvider[provider] ?: ModelCatalog.recommendedModelId(provider)

    private fun buildState() = State(
        savedProviders = keyStore.getAllSaved(),
        settings = settingsStore.load(),
    )

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                LlmSettingsViewModel(app.secureKeyStore, app.llmSettingsStore)
            }
        }
        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
