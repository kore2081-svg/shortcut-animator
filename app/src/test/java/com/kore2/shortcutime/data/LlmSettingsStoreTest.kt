package com.kore2.shortcutime.data

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.llm.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmSettingsStoreTest {
    private lateinit var clock: FakeClock
    private lateinit var store: LlmSettingsStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("llm_settings", 0).edit().clear().apply()
        clock = FakeClock(today = "2026-04-19")
        store = LlmSettingsStore(context, clock)
    }

    @Test
    fun `default settings when nothing saved`() {
        val s = store.load()
        assertNull(s.activeProvider)
        assertEquals(50, s.dailyCallCap)
        assertEquals(0, s.todayCallCount)
        assertEquals("2026-04-19", s.todayResetDate)
    }

    @Test
    fun `setActiveProvider persists`() {
        store.setActiveProvider(ProviderId.CLAUDE)
        assertEquals(ProviderId.CLAUDE, store.load().activeProvider)
        store.setActiveProvider(null)
        assertNull(store.load().activeProvider)
    }

    @Test
    fun `setModel stores per-provider mapping`() {
        store.setModel(ProviderId.OPENAI, "gpt-4o")
        store.setModel(ProviderId.GEMINI, "gemini-2.5-pro")
        val s = store.load()
        assertEquals("gpt-4o", s.modelByProvider[ProviderId.OPENAI])
        assertEquals("gemini-2.5-pro", s.modelByProvider[ProviderId.GEMINI])
    }

    @Test
    fun `setDailyCap clamps to 10-500 range`() {
        store.setDailyCap(5)
        assertEquals(10, store.load().dailyCallCap)
        store.setDailyCap(1000)
        assertEquals(500, store.load().dailyCallCap)
        store.setDailyCap(75)
        assertEquals(75, store.load().dailyCallCap)
    }

    @Test
    fun `incrementCallCount on same day adds 1`() {
        store.incrementCallCount()
        store.incrementCallCount()
        assertEquals(2, store.load().todayCallCount)
    }

    @Test
    fun `load resets counter when date changed`() {
        store.incrementCallCount()
        store.incrementCallCount()
        assertEquals(2, store.load().todayCallCount)

        clock.today = "2026-04-20"
        val reloaded = store.load()
        assertEquals(0, reloaded.todayCallCount)
        assertEquals("2026-04-20", reloaded.todayResetDate)
    }
}

class FakeClock(var today: String, var millis: Long = 0) : Clock {
    override fun today(): String = today
    override fun nowMillis(): Long = millis
}
