package com.kore2.shortcutime.llm

interface LlmAdapter {
    val providerId: ProviderId
    suspend fun validateKey(apiKey: String): Result<Unit>
    suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult>
    suspend fun callWithPrompt(apiKey: String, model: String, prompt: String): Result<GenerationResult>
}
