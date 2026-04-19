package com.kore2.shortcutime.llm

object PromptBuilder {
    fun build(shortcut: String, expansion: String, count: Int): String {
        val s = shortcut.replace("\\", "\\\\").replace("\"", "\\\"")
        val e = expansion.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            You generate example sentences illustrating the phrase: "$e"

            Rules:
            - Write $count natural example sentences that use this phrase in realistic contexts.
            - NEVER use the abbreviation "$s" in any sentence. Write the full phrase or a paraphrase only.
            - Detect the language of the phrase automatically and write ALL examples in THE SAME LANGUAGE.

            Return ONLY a JSON object with no prose, no markdown:
            {"examples": ["sentence 1", "sentence 2", ...]}
        """.trimIndent()
    }

    fun buildTranslation(text: String, targetLanguage: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            Translate the following sentence into $targetLanguage.
            Return ONLY a JSON object with no prose, no markdown:
            {"examples": ["<translated sentence>"]}
            Sentence: "$escaped"
        """.trimIndent()
    }
}
