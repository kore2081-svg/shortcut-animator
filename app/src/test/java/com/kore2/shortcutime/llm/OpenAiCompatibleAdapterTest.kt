package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiCompatibleAdapter

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        adapter = OpenAiCompatibleAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            providerId = ProviderId.OPENAI,
            httpClient = HttpClientFactory.create(debug = false),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        assertTrue(adapter.validateKey("sk-ok").isSuccess)
    }

    @Test
    fun `validateKey 401 maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad"}}"""))
        val r = adapter.validateKey("sk-bad")
        assertTrue(r.isFailure)
        val err = (r.exceptionOrNull() as LlmException).error
        assertTrue(err is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val body = """
            {"choices":[{"message":{"content":"{\"examples\":[\"a\",\"b\",\"c\"]}"},"finish_reason":"stop"}]}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "btw", "by the way", 3)
        assertTrue(r.isSuccess)
        assertEquals(listOf("a", "b", "c"), r.getOrThrow().examples)
    }

    @Test
    fun `generateExamples 429 maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"type":"rate_limit_exceeded"}}"""))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue(r.isFailure)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.RateLimited)
    }

    @Test
    fun `generateExamples 500 maps to ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ServerError)
    }

    @Test
    fun `finish_reason length maps to Truncated`() = runBlocking {
        val body = """{"choices":[{"message":{"content":"{\"examples\":[\"a\"]}"},"finish_reason":"length"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `finish_reason content_filter maps to ContentFiltered`() = runBlocking {
        val body = """{"choices":[{"message":{"content":"{\"examples\":[]}"},"finish_reason":"content_filter"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `error body invalid_api_key maps to InvalidKey even on 400`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"type":"invalid_api_key","message":"x"}}"""))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `timeout maps to Timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val fastClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val fastAdapter = OpenAiCompatibleAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            providerId = ProviderId.OPENAI,
            httpClient = fastClient,
        )
        val r = fastAdapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Timeout)
    }
}
