package com.kore2.shortcutime.llm

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

class OpenAiCompatibleAdapter(
    private val baseUrl: String,
    override val providerId: ProviderId,
    private val httpClient: OkHttpClient,
) : LlmAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw LlmException(mapHttpError(resp.code, resp.body?.string().orEmpty()))
                }
            }
        }.mapExceptionToLlm()
    }

    override suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult> = callLlm(apiKey, model, PromptBuilder.build(shortcut, expansion, count), count)

    override suspend fun callWithPrompt(
        apiKey: String,
        model: String,
        prompt: String,
    ): Result<GenerationResult> = callLlm(apiKey, model, prompt, 1)

    private suspend fun callLlm(
        apiKey: String,
        model: String,
        prompt: String,
        count: Int,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val payload = """
            {"model":${json.encodeToString(String.serializer(), model)},"max_tokens":512,
             "response_format":{"type":"json_object"},
             "messages":[{"role":"user","content":${json.encodeToString(String.serializer(), prompt)}}]}
        """.trimIndent()

        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, body))
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                val first = parsed.choices.firstOrNull()
                    ?: throw LlmException(LlmError.ParseFailure)
                when (first.finish_reason) {
                    "length" -> throw LlmException(LlmError.Truncated)
                    "content_filter" -> throw LlmException(LlmError.ContentFiltered)
                }
                val content = first.message.content
                ParseExamples.parse(content, count).getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun mapHttpError(code: Int, body: String): LlmError {
        // body 의 error.type 먼저 확인
        if (body.contains("\"invalid_api_key\"") || body.contains("\"insufficient_quota\"")) {
            return LlmError.InvalidKey
        }
        if (body.contains("\"rate_limit_exceeded\"")) return LlmError.RateLimited
        return when (code) {
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503 -> LlmError.ServerError
            404 -> LlmError.Unknown("endpoint not found")
            else -> LlmError.Unknown("http $code: ${body.take(200)}")
        }
    }

    private fun <T> Result<T>.mapExceptionToLlm(): Result<T> = recoverCatching { t ->
        if (t is kotlinx.coroutines.CancellationException) throw t
        throw when (t) {
            is LlmException -> t
            is SocketTimeoutException -> LlmException(LlmError.Timeout)
            is IOException -> LlmException(LlmError.Network)
            else -> LlmException(LlmError.Unknown(t.message ?: t::class.java.simpleName))
        }
    }

    @Serializable
    private data class ChatCompletionResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(
        val message: Message,
        val finish_reason: String? = null,
    )

    @Serializable
    private data class Message(val content: String = "")

    companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com"
        const val GROK_BASE_URL = "https://api.x.ai"
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    }
}
