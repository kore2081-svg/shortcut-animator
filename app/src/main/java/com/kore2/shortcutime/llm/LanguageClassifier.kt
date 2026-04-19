package com.kore2.shortcutime.llm

object LanguageClassifier {
    enum class Language { KOREAN, ENGLISH }

    fun classify(text: String): Language {
        val hasHangul = text.any { c ->
            c in '\uAC00'..'\uD7A3' ||  // Hangul Syllables
            c in '\u1100'..'\u11FF' ||  // Hangul Jamo
            c in '\u3130'..'\u318F'     // Hangul Compatibility Jamo
        }
        return if (hasHangul) Language.KOREAN else Language.ENGLISH
    }
}
