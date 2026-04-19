package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GeminiAdapter

    @Before fun setup() {
        server = MockWebServer(); server.start()
        adapter = GeminiAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            httpClient = HttpClientFactory.create(),
        )
    }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        assertTrue(adapter.validateKey("g-key").isSuccess)
    }

    @Test
    fun `validateKey 400 INVALID_ARGUMENT maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid"}}"""))
        val r = adapter.validateKey("bad")
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val inner = """{\"examples\":[\"x\",\"y\"]}"""
        val body = """{"candidates":[{"content":{"parts":[{"text":"$inner"}]},"finishReason":"STOP"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "gemini-2.5-flash", "s", "e", 2)
        assertTrue(r.isSuccess)
        assertEquals(listOf("x", "y"), r.getOrThrow().examples)
    }

    @Test
    fun `finishReason SAFETY maps to ContentFiltered`() = runBlocking {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"examples\":[]}"}]},"finishReason":"SAFETY"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `finishReason MAX_TOKENS maps to Truncated`() = runBlocking {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"examples\":[\"a\"]}"}]},"finishReason":"MAX_TOKENS"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `empty candidates maps to ContentFiltered`() = runBlocking {
        val body = """{"candidates":[]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }
}
