package com.kore2.shortcutime.llm

import okhttp3.OkHttpClient

class LlmRegistry(private val httpClient: OkHttpClient) {
    fun adapterFor(provider: ProviderId): LlmAdapter = when (provider) {
        ProviderId.OPENAI -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.OPENAI_BASE_URL, ProviderId.OPENAI, httpClient)
        ProviderId.GROK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.GROK_BASE_URL, ProviderId.GROK, httpClient)
        ProviderId.DEEPSEEK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.DEEPSEEK_BASE_URL, ProviderId.DEEPSEEK, httpClient)
        ProviderId.CLAUDE -> ClaudeAdapter(httpClient = httpClient)
        ProviderId.GEMINI -> GeminiAdapter(httpClient = httpClient)
    }
}
