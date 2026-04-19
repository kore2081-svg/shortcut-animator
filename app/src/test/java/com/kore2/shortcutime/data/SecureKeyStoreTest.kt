package com.kore2.shortcutime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.llm.ProviderId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureKeyStoreTest {
    private lateinit var store: SecureKeyStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_api_keys", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = SecureKeyStore(prefs)
    }

    @After
    fun teardown() {
        ProviderId.values().forEach { store.clear(it) }
    }

    @Test
    fun `save and get round trip`() {
        store.save(ProviderId.OPENAI, "sk-test-key-123")
        assertEquals("sk-test-key-123", store.get(ProviderId.OPENAI))
    }

    @Test
    fun `get returns null for unsaved provider`() {
        assertNull(store.get(ProviderId.CLAUDE))
    }

    @Test
    fun `clear removes saved key`() {
        store.save(ProviderId.GEMINI, "gm-key")
        store.clear(ProviderId.GEMINI)
        assertNull(store.get(ProviderId.GEMINI))
    }

    @Test
    fun `different providers isolated`() {
        store.save(ProviderId.OPENAI, "a")
        store.save(ProviderId.CLAUDE, "b")
        assertEquals("a", store.get(ProviderId.OPENAI))
        assertEquals("b", store.get(ProviderId.CLAUDE))
    }

    @Test
    fun `getAllSaved returns only providers with keys`() {
        store.save(ProviderId.OPENAI, "x")
        store.save(ProviderId.GROK, "y")
        val saved = store.getAllSaved()
        assertEquals(setOf(ProviderId.OPENAI, ProviderId.GROK), saved)
    }

    @Test
    fun `empty string saves and clears like null`() {
        store.save(ProviderId.DEEPSEEK, "   ")
        assertNull(store.get(ProviderId.DEEPSEEK))
        assertTrue(ProviderId.DEEPSEEK !in store.getAllSaved())
    }
}
