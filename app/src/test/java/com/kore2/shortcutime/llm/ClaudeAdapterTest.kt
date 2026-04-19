package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ClaudeAdapter

    @Before fun setup() {
        server = MockWebServer(); server.start()
        adapter = ClaudeAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            httpClient = HttpClientFactory.create(),
        )
    }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"x","stop_reason":"end_turn","content":[{"type":"text","text":"ok"}]}"""))
        assertTrue(adapter.validateKey("anthropic-key").isSuccess)
    }

    @Test
    fun `validateKey 401 maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error"}}"""))
        val r = adapter.validateKey("bad")
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val content = """{\"examples\":[\"a\",\"b\"]}"""
        val body = """{"content":[{"type":"text","text":"$content"}],"stop_reason":"end_turn"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "claude-haiku-4-5-20251001", "s", "e", 2)
        assertTrue(r.isSuccess)
        assertEquals(listOf("a", "b"), r.getOrThrow().examples)
    }

    @Test
    fun `stop_reason max_tokens maps to Truncated`() = runBlocking {
        val body = """{"content":[{"type":"text","text":"{\"examples\":[\"a\"]}"}],"stop_reason":"max_tokens"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `stop_reason refusal maps to ContentFiltered`() = runBlocking {
        val body = """{"content":[{"type":"text","text":"{\"examples\":[]}"}],"stop_reason":"refusal"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `overloaded_error maps to ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(529).setBody("""{"type":"error","error":{"type":"overloaded_error"}}"""))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ServerError)
    }

    @Test
    fun `rate_limit_error maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"type":"error","error":{"type":"rate_limit_error"}}"""))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.RateLimited)
    }
}
