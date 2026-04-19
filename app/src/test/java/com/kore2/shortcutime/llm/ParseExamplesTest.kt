package com.kore2.shortcutime.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseExamplesTest {
    @Test
    fun `clean JSON object with examples array`() {
        val result = ParseExamples.parse("""{"examples":["one","two","three"]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two", "three"), result.getOrThrow().examples)
        assertEquals(3, result.getOrThrow().requestedCount)
    }

    @Test
    fun `JSON wrapped in markdown code fences`() {
        val raw = "```json\n{\"examples\":[\"a\",\"b\"]}\n```"
        val result = ParseExamples.parse(raw, 2)
        assertTrue(result.isSuccess)
        assertEquals(listOf("a", "b"), result.getOrThrow().examples)
    }

    @Test
    fun `fewer examples than requested is still success`() {
        val result = ParseExamples.parse("""{"examples":["x"]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().examples.size)
        assertEquals(3, result.getOrThrow().requestedCount)
    }

    @Test
    fun `null and blank entries filtered out`() {
        val result = ParseExamples.parse("""{"examples":["one","","  ","two"]}""", 4)
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two"), result.getOrThrow().examples)
    }

    @Test
    fun `empty examples array returns success with zero items`() {
        val result = ParseExamples.parse("""{"examples":[]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().examples.size)
    }

    @Test
    fun `malformed JSON falls back to regex extraction`() {
        val raw = """sure! here are some: "first example" "second example" "third example""""
        val result = ParseExamples.parse(raw, 3)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().examples.size)
    }

    @Test
    fun `completely unusable text returns ParseFailure`() {
        val result = ParseExamples.parse("totally unparseable no quotes either", 3)
        assertTrue(result.isFailure)
        val err = (result.exceptionOrNull() as LlmException).error
        assertTrue(err is LlmError.ParseFailure)
    }
}
