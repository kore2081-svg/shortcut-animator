package com.kore2.shortcutime.llm

object PromptBuilder {
    fun build(shortcut: String, expansion: String, count: Int): String {
        val s = shortcut.replace("\\", "\\\\").replace("\"", "\\\"")
        val e = expansion.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            You generate example sentences demonstrating a text-expansion shortcut.

            Shortcut: "$s"
            Expands to: "$e"

            Write $count natural example sentences that naturally use the shortcut's expansion in realistic contexts.
            Detect the language of the expansion automatically and write examples in THE SAME LANGUAGE.

            Return ONLY a JSON object of this exact shape with no prose, no markdown:
            {"examples": ["sentence 1", "sentence 2", ...]}
        """.trimIndent()
    }
}
