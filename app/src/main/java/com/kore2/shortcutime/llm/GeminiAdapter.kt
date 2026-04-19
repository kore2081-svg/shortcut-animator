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
import java.net.URLEncoder

class GeminiAdapter(
    private val baseUrl: String = PRODUCTION_BASE_URL,
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
) : LlmAdapter {

    override val providerId: ProviderId = ProviderId.GEMINI
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val req = Request.Builder().url("$baseUrl/v1beta/models?key=$key").get().build()
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
        val body = """
            {"contents":[{"parts":[{"text":${json.encodeToString(String.serializer(), prompt)}}]}],
             "generationConfig":{"responseMimeType":"application/json","maxOutputTokens":512}}
        """.trimIndent()
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val req = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:generateContent?key=$key")
            .post(body.toRequestBody(jsonMedia))
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, raw))
                val parsed = json.decodeFromString(GenerateContentResponse.serializer(), raw)
                val candidate = parsed.candidates.firstOrNull()
                    ?: throw LlmException(LlmError.ContentFiltered)
                when (candidate.finishReason) {
                    "MAX_TOKENS" -> throw LlmException(LlmError.Truncated)
                    "SAFETY", "RECITATION", "BLOCKLIST" -> throw LlmException(LlmError.ContentFiltered)
                }
                val text = candidate.content?.parts?.firstOrNull()?.text
                    ?: throw LlmException(LlmError.ParseFailure)
                ParseExamples.parse(text, count).getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun mapHttpError(code: Int, body: String): LlmError {
        if (body.contains("\"INVALID_ARGUMENT\"") && body.contains("API key", ignoreCase = true)) {
            return LlmError.InvalidKey
        }
        return when (code) {
            400 -> if (body.contains("\"INVALID_ARGUMENT\"")) LlmError.InvalidKey else LlmError.Unknown("http 400: ${body.take(200)}")
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503 -> LlmError.ServerError
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
    private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
    )

    @Serializable
    private data class Content(val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String = "")

    companion object {
        const val PRODUCTION_BASE_URL = "https://generativelanguage.googleapis.com"
    }
}
