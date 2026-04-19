package com.kore2.shortcutime.llm

import com.kore2.shortcutime.llm.ProviderId.*

object ModelCatalog {
    private val entries: Map<ProviderId, List<ModelInfo>> = mapOf(
        CLAUDE to listOf(
            ModelInfo("claude-haiku-4-5-20251001", "Claude Haiku 4.5", true),
            ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", false),
            ModelInfo("claude-opus-4-7", "Claude Opus 4.7", false),
        ),
        OPENAI to listOf(
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", true),
            ModelInfo("gpt-4o", "GPT-4o", false),
            ModelInfo("gpt-4.1", "GPT-4.1", false),
        ),
        GEMINI to listOf(
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", true),
            ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", false),
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", false),
        ),
        GROK to listOf(
            ModelInfo("grok-2-mini", "Grok 2 Mini", true),
            ModelInfo("grok-2", "Grok 2", false),
            ModelInfo("grok-3", "Grok 3", false),
        ),
        DEEPSEEK to listOf(
            ModelInfo("deepseek-chat", "DeepSeek V3", true),
            ModelInfo("deepseek-reasoner", "DeepSeek R1", false),
        ),
    )

    fun modelsFor(provider: ProviderId): List<ModelInfo> =
        entries.getValue(provider)

    fun recommendedModelId(provider: ProviderId): String =
        modelsFor(provider).first { it.isRecommended }.id
}
