package com.kore2.shortcutime.llm

import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AdapterRegistry {
    fun adapterFor(provider: ProviderId): LlmAdapter
}

class ExampleGenerationService(
    private val keyStore: SecureKeyStore,
    private val settingsStore: LlmSettingsStore,
    private val registry: AdapterRegistry,
) {
    sealed class Outcome {
        data class Success(val examples: List<String>) : Outcome()
        data class Partial(val examples: List<String>, val requested: Int) : Outcome()
        data class Failure(val error: LlmError) : Outcome()
        object NoActiveProvider : Outcome()
        object NoKey : Outcome()
        object DailyCapExceeded : Outcome()
    }

    private val counterMutex = Mutex()

    suspend fun generate(shortcut: String, expansion: String, count: Int): Outcome {
        val snapshot = settingsStore.load()
        val provider = snapshot.activeProvider ?: return Outcome.NoActiveProvider
        val key = keyStore.get(provider) ?: return Outcome.NoKey

        counterMutex.withLock {
            val current = settingsStore.load()
            if (current.todayCallCount >= current.dailyCallCap) return Outcome.DailyCapExceeded
            settingsStore.incrementCallCount()
        }

        val model = settingsStore.load().modelByProvider[provider]
            ?: ModelCatalog.recommendedModelId(provider)

        val adapter = registry.adapterFor(provider)
        val result = adapter.generateExamples(key, model, shortcut, expansion, count)
        return result.fold(
            onSuccess = { gen ->
                when {
                    gen.examples.isEmpty() -> Outcome.Failure(LlmError.ParseFailure)
                    gen.examples.size >= count -> Outcome.Success(gen.examples.take(count))
                    else -> Outcome.Partial(gen.examples, count)
                }
            },
            onFailure = { t ->
                val err = (t as? LlmException)?.error ?: LlmError.Unknown(t.message.orEmpty())
                Outcome.Failure(err)
            },
        )
    }
}
