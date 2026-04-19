package com.kore2.shortcutime.llm

data class GenerationResult(
    val examples: List<String>,
    val requestedCount: Int,
)
