package com.kore2.shortcutime.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class ClaudeAdapter(
    private val baseUrl: String = PRODUCTION_BASE_URL,
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
) : LlmAdapter {

    override val providerId: ProviderId = ProviderId.CLAUDE
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = """{"model":"claude-haiku-4-5-20251001","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
        val req = requestBuilder(apiKey).url("$baseUrl/v1/messages").post(body.toRequestBody(jsonMedia)).build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, resp.body?.string().orEmpty()))
            }
        }.mapExceptionToLlm()
    }

    override suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.build(shortcut, expansion, count)
        val body = """
            {"model":${json.encodeToString(String.serializer(), model)},"max_tokens":512,
             "messages":[{"role":"user","content":${json.encodeToString(String.serializer(), prompt)}}]}
        """.trimIndent()
        val req = requestBuilder(apiKey).url("$baseUrl/v1/messages").post(body.toRequestBody(jsonMedia)).build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, raw))
                val parsed = json.decodeFromString(MessageResponse.serializer(), raw)
                when (parsed.stop_reason) {
                    "max_tokens" -> throw LlmException(LlmError.Truncated)
                    "refusal" -> throw LlmException(LlmError.ContentFiltered)
                }
                val text = parsed.content.firstOrNull { it.type == "text" }?.text
                    ?: throw LlmException(LlmError.ParseFailure)
                ParseExamples.parse(text, count).getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun requestBuilder(apiKey: String): Request.Builder =
        Request.Builder()
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")

    private fun mapHttpError(code: Int, body: String): LlmError {
        if (body.contains("\"authentication_error\"")) return LlmError.InvalidKey
        if (body.contains("\"rate_limit_error\"")) return LlmError.RateLimited
        if (body.contains("\"overloaded_error\"")) return LlmError.ServerError
        return when (code) {
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503, 529 -> LlmError.ServerError
            404 -> LlmError.Unknown("endpoint not found")
            else -> LlmError.Unknown("http $code: ${body.take(200)}")
        }
    }

    private fun <T> Result<T>.mapExceptionToLlm(): Result<T> = recoverCatching { t ->
        if (t is CancellationException) throw t
        throw when (t) {
            is LlmException -> t
            is SocketTimeoutException -> LlmException(LlmError.Timeout)
            is IOException -> LlmException(LlmError.Network)
            else -> LlmException(LlmError.Unknown(t.message ?: t::class.java.simpleName))
        }
    }

    @Serializable
    private data class MessageResponse(
        val content: List<ContentBlock> = emptyList(),
        val stop_reason: String? = null,
    )

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String = "")

    companion object {
        const val PRODUCTION_BASE_URL = "https://api.anthropic.com"
    }
}
