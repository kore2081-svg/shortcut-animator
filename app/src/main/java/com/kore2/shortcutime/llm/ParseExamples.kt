package com.kore2.shortcutime.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ParseExamples {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fencePattern = Regex("^```(?:json)?\\s*|\\s*```$", RegexOption.MULTILINE)
    private val quotedStringPattern = Regex("\"([^\"\\\\]+)\"")

    @Serializable
    private data class Payload(val examples: List<String> = emptyList())

    fun parse(rawText: String, requestedCount: Int): Result<GenerationResult> {
        val stripped = rawText.trim().replace(fencePattern, "").trim()

        // 1차: JSON 파싱
        val jsonAttempt = runCatching {
            json.decodeFromString(Payload.serializer(), stripped)
        }
        if (jsonAttempt.isSuccess) {
            val examples = jsonAttempt.getOrThrow().examples
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return Result.success(GenerationResult(examples, requestedCount))
        }

        // 2차: 정규식으로 따옴표 문자열 추출
        val regexMatches = quotedStringPattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && it.length > 2 }
            .toList()
        if (regexMatches.isNotEmpty()) {
            return Result.success(GenerationResult(regexMatches, requestedCount))
        }

        return Result.failure(LlmException(LlmError.ParseFailure))
    }
}
