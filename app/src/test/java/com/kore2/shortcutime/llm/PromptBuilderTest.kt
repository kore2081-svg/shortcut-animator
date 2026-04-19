package com.kore2.shortcutime.llm

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun `prompt contains shortcut expansion count and JSON instruction`() {
        val prompt = PromptBuilder.build(shortcut = "btw", expansion = "by the way", count = 3)
        assertTrue(prompt.contains("\"btw\""))
        assertTrue(prompt.contains("\"by the way\""))
        assertTrue(prompt.contains("3 natural example sentences"))
        assertTrue(prompt.contains("{\"examples\":"))
        assertTrue(prompt.contains("THE SAME LANGUAGE"))
    }

    @Test
    fun `prompt escapes quotes in shortcut`() {
        val prompt = PromptBuilder.build("say \"hi\"", "hello", 1)
        assertTrue(prompt.contains("\\\"hi\\\""))
    }
}
