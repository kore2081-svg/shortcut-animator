package com.kore2.shortcutime.llm

import com.kore2.shortcutime.llm.LanguageClassifier.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageClassifierTest {
    @Test
    fun `pure English is classified ENGLISH`() {
        assertEquals(Language.ENGLISH, LanguageClassifier.classify("by the way"))
    }

    @Test
    fun `pure Korean is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("그런데 말이야"))
    }

    @Test
    fun `mixed with any hangul is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("BTW 그건 그렇고"))
    }

    @Test
    fun `jamo (초성) is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("ㅋㅋㅋ"))
    }

    @Test
    fun `empty string defaults to ENGLISH`() {
        assertEquals(Language.ENGLISH, LanguageClassifier.classify(""))
    }
}
