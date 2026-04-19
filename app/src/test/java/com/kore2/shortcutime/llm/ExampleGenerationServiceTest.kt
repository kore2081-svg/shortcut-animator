package com.kore2.shortcutime.llm

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.data.FakeClock
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleGenerationServiceTest {
    private lateinit var ctx: android.content.Context
    private lateinit var keyStore: SecureKeyStore
    private lateinit var settingsStore: LlmSettingsStore
    private lateinit var fakeAdapter: FakeAdapter
    private lateinit var registry: FakeRegistry
    private lateinit var clock: FakeClock
    private lateinit var service: ExampleGenerationService

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("llm_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val keyPrefs = ctx.getSharedPreferences("test_api_keys", android.content.Context.MODE_PRIVATE)
        keyPrefs.edit().clear().apply()
        clock = FakeClock(today = "2026-04-19")
        keyStore = SecureKeyStore(keyPrefs)
        settingsStore = LlmSettingsStore(ctx, clock)
        fakeAdapter = FakeAdapter(ProviderId.OPENAI)
        registry = FakeRegistry(fakeAdapter)
        service = ExampleGenerationService(keyStore, settingsStore, registry)
    }

    @Test
    fun `NoActiveProvider when none set`() = runBlocking {
        val o = service.generate("btw", "by the way", 3)
        assertTrue(o is ExampleGenerationService.Outcome.NoActiveProvider)
    }

    @Test
    fun `NoKey when active provider has no key`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        val o = service.generate("btw", "by the way", 3)
        assertTrue(o is ExampleGenerationService.Outcome.NoKey)
    }

    @Test
    fun `Success when adapter returns exact count`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(listOf("a", "b", "c"), 3))
        val o = service.generate("s", "e", 3)
        assertTrue(o is ExampleGenerationService.Outcome.Success)
        assertEquals(listOf("a", "b", "c"), (o as ExampleGenerationService.Outcome.Success).examples)
        assertEquals(1, settingsStore.load().todayCallCount)
    }

    @Test
    fun `Partial when fewer returned`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 3))
        val o = service.generate("s", "e", 3) as ExampleGenerationService.Outcome.Partial
        assertEquals(listOf("a"), o.examples)
        assertEquals(3, o.requested)
    }

    @Test
    fun `Failure when adapter fails`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.failure(LlmException(LlmError.RateLimited))
        val o = service.generate("s", "e", 3)
        assertTrue(o is ExampleGenerationService.Outcome.Failure)
        assertTrue((o as ExampleGenerationService.Outcome.Failure).error is LlmError.RateLimited)
        assertEquals(1, settingsStore.load().todayCallCount)
    }

    @Test
    fun `zero examples with success returns ParseFailure Outcome`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(emptyList(), 3))
        val o = service.generate("s", "e", 3) as ExampleGenerationService.Outcome.Failure
        assertTrue(o.error is LlmError.ParseFailure)
    }

    @Test
    fun `DailyCapExceeded does not call adapter`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        ctx.getSharedPreferences("llm_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("daily_cap", 2).apply()
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 1))
        service.generate("s", "e", 1)
        service.generate("s", "e", 1)
        val o = service.generate("s", "e", 1)
        assertTrue(o is ExampleGenerationService.Outcome.DailyCapExceeded)
        assertEquals(2, fakeAdapter.callCount)
    }

    @Test
    fun `date boundary resets counter`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        ctx.getSharedPreferences("llm_settings", android.content.Context.MODE_PRIVATE)
            .edit().putInt("daily_cap", 1).apply()
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 1))
        service.generate("s", "e", 1)
        val blocked = service.generate("s", "e", 1)
        assertTrue(blocked is ExampleGenerationService.Outcome.DailyCapExceeded)
        clock.today = "2026-04-20"
        val retry = service.generate("s", "e", 1)
        assertTrue(retry is ExampleGenerationService.Outcome.Success)
    }

    class FakeAdapter(override val providerId: ProviderId) : LlmAdapter {
        var result: Result<GenerationResult> = Result.success(GenerationResult(emptyList(), 0))
        var callCount = 0
        override suspend fun validateKey(apiKey: String): Result<Unit> = Result.success(Unit)
        override suspend fun generateExamples(
            apiKey: String, model: String, shortcut: String, expansion: String, count: Int,
        ): Result<GenerationResult> {
            callCount++
            return result
        }
    }

    class FakeRegistry(private val adapter: LlmAdapter) : AdapterRegistry {
        override fun adapterFor(provider: ProviderId): LlmAdapter = adapter
    }
}
