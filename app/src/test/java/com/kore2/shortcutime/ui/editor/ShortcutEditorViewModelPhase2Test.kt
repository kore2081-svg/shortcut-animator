package com.kore2.shortcutime.ui.editor

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.data.FakeClock
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.GenerationResult
import com.kore2.shortcutime.llm.LlmAdapter
import com.kore2.shortcutime.llm.ProviderId
import com.kore2.shortcutime.llm.AdapterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShortcutEditorViewModelPhase2Test {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setMainDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `generate success emits event and stores AUTO sourceType`() = runTest(dispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("llm_settings", 0).edit().clear().apply()
        val repo = FolderRepository(ctx)
        val folder = FolderItem(title = "T")
        repo.saveFolder(folder)

        val clock = FakeClock("2026-04-19")
        val keyPrefs = ctx.getSharedPreferences("test_keys_phase2_vm", android.content.Context.MODE_PRIVATE)
        keyPrefs.edit().clear().apply()
        val keyStore = SecureKeyStore(keyPrefs).apply { save(ProviderId.OPENAI, "sk") }
        val settings = LlmSettingsStore(ctx, clock).apply { setActiveProvider(ProviderId.OPENAI) }
        val registry = object : AdapterRegistry {
            override fun adapterFor(provider: ProviderId): LlmAdapter = object : LlmAdapter {
                override val providerId = ProviderId.OPENAI
                override suspend fun validateKey(apiKey: String) = Result.success(Unit)
                override suspend fun generateExamples(
                    apiKey: String, model: String, shortcut: String, expansion: String, count: Int,
                ) = Result.success(GenerationResult(listOf("BTW, I'll be late.", "안녕하세요"), count))
            }
        }
        val service = ExampleGenerationService(keyStore, settings, registry)
        val vm = ShortcutEditorViewModel(repo, service, folder.id, null)

        val emitted = mutableListOf<ShortcutEditorViewModel.EditorEvent>()
        val job = launch { vm.events.take(1).toList(emitted) }
        vm.onGenerateExamplesClicked("btw", "by the way", 2)
        advanceUntilIdle()

        assertTrue(emitted.first() is ShortcutEditorViewModel.EditorEvent.GenerateSuccess)
        val examples = vm.workingExamples.first()
        assertEquals(2, examples.size)
        assertTrue(examples.any { it.english.contains("BTW") })
        assertTrue(examples.any { it.korean.contains("안녕") })
        examples.forEach { assertEquals(com.kore2.shortcutime.data.ExampleSourceType.AUTO, it.sourceType) }

        job.cancel()
    }
}
