package com.kore2.shortcutime.ime

class HangulComposer {
    private var initial: Char? = null
    private var medial: Char? = null
    private var final: Char? = null

    fun hasComposition(): Boolean = initial != null || medial != null || final != null

    fun clear() {
        initial = null
        medial = null
        final = null
    }

    fun currentText(): String {
        return composeCurrent()?.toString().orEmpty()
    }

    fun input(jamo: Char): ComposerResult {
        return if (isVowel(jamo)) {
            inputVowel(jamo)
        } else {
            inputConsonant(jamo)
        }
    }

    fun backspace(): Boolean {
        when {
            final != null -> {
                final = null
                return true
            }
            medial != null -> {
                val reduced = reduceMedial(medial!!)
                if (reduced != null) {
                    medial = reduced
                } else {
                    medial = null
                }
                return true
            }
            initial != null -> {
                initial = null
                return true
            }
        }
        return false
    }

    private fun inputConsonant(jamo: Char): ComposerResult {
        if (initial == null && medial == null) {
            initial = jamo
            return ComposerResult(update = currentText())
        }

        if (initial != null && medial == null) {
            val commit = currentText()
            initial = jamo
            return ComposerResult(commit = commit, update = currentText())
        }

        if (initial != null && medial != null && final == null) {
            final = jamo
            return ComposerResult(update = currentText())
        }

        if (initial != null && medial != null && final != null) {
            val combined = combineFinal(final!!, jamo)
            if (combined != null) {
                final = combined
                return ComposerResult(update = currentText())
            }

            val commit = currentText()
            clear()
            initial = jamo
            return ComposerResult(commit = commit, update = currentText())
        }

        val commit = currentText()
        clear()
        initial = jamo
        return ComposerResult(commit = commit, update = currentText())
    }

    private fun inputVowel(jamo: Char): ComposerResult {
        if (initial == null && medial == null) {
            medial = jamo
            return ComposerResult(update = currentText())
        }

        if (initial != null && medial == null) {
            medial = jamo
            return ComposerResult(update = currentText())
        }

        if (initial == null && medial != null) {
            val combined = combineMedial(medial!!, jamo)
            if (combined != null) {
                medial = combined
                return ComposerResult(update = currentText())
            }
            val commit = currentText()
            clear()
            medial = jamo
            return ComposerResult(commit = commit, update = currentText())
        }

        if (initial != null && medial != null && final == null) {
            val combined = combineMedial(medial!!, jamo)
            if (combined != null) {
                medial = combined
                return ComposerResult(update = currentText())
            }
            val commit = currentText()
            clear()
            medial = jamo
            return ComposerResult(commit = commit, update = currentText())
        }

        if (initial != null && medial != null && final != null) {
            val split = splitFinal(final!!)
            return if (split != null) {
                val commitChar = composeSyllable(initial!!, medial!!, split.first)
                clear()
                initial = split.second
                medial = jamo
                ComposerResult(commit = commitChar.toString(), update = currentText())
            } else {
                val carry = final!!
                val commitChar = composeSyllable(initial!!, medial!!, null)
                clear()
                initial = carry
                medial = jamo
                ComposerResult(commit = commitChar.toString(), update = currentText())
            }
        }

        val commit = currentText()
        clear()
        medial = jamo
        return ComposerResult(commit = commit, update = currentText())
    }

    private fun composeCurrent(): Char? {
        if (initial != null && medial != null) {
            return composeSyllable(initial!!, medial!!, final)
        }
        return medial ?: initial
    }

    private fun composeSyllable(initial: Char, medial: Char, final: Char?): Char {
        val lIndex = INITIAL_INDEX[initial] ?: return initial
        val vIndex = MEDIAL_INDEX[medial] ?: return medial
        val tIndex = final?.let { FINAL_INDEX[it] } ?: 0
        return (HANGUL_BASE + ((lIndex * 21) + vIndex) * 28 + tIndex).toChar()
    }

    private fun isVowel(jamo: Char): Boolean = MEDIAL_INDEX.containsKey(jamo)

    private fun combineMedial(first: Char, second: Char): Char? = when ("$first$second") {
        "ㅗㅏ" -> 'ㅘ'
        "ㅗㅐ" -> 'ㅙ'
        "ㅗㅣ" -> 'ㅚ'
        "ㅜㅓ" -> 'ㅝ'
        "ㅜㅔ" -> 'ㅞ'
        "ㅜㅣ" -> 'ㅟ'
        "ㅡㅣ" -> 'ㅢ'
        else -> null
    }

    private fun reduceMedial(medial: Char): Char? = when (medial) {
        'ㅘ', 'ㅙ', 'ㅚ' -> 'ㅗ'
        'ㅝ', 'ㅞ', 'ㅟ' -> 'ㅜ'
        'ㅢ' -> 'ㅡ'
        else -> null
    }

    private fun combineFinal(first: Char, second: Char): Char? = when ("$first$second") {
        "ㄱㅅ" -> 'ㄳ'
        "ㄴㅈ" -> 'ㄵ'
        "ㄴㅎ" -> 'ㄶ'
        "ㄹㄱ" -> 'ㄺ'
        "ㄹㅁ" -> 'ㄻ'
        "ㄹㅂ" -> 'ㄼ'
        "ㄹㅅ" -> 'ㄽ'
        "ㄹㅌ" -> 'ㄾ'
        "ㄹㅍ" -> 'ㄿ'
        "ㄹㅎ" -> 'ㅀ'
        "ㅂㅅ" -> 'ㅄ'
        else -> null
    }

    private fun splitFinal(value: Char): Pair<Char?, Char> ? = when (value) {
        'ㄳ' -> 'ㄱ' to 'ㅅ'
        'ㄵ' -> 'ㄴ' to 'ㅈ'
        'ㄶ' -> 'ㄴ' to 'ㅎ'
        'ㄺ' -> 'ㄹ' to 'ㄱ'
        'ㄻ' -> 'ㄹ' to 'ㅁ'
        'ㄼ' -> 'ㄹ' to 'ㅂ'
        'ㄽ' -> 'ㄹ' to 'ㅅ'
        'ㄾ' -> 'ㄹ' to 'ㅌ'
        'ㄿ' -> 'ㄹ' to 'ㅍ'
        'ㅀ' -> 'ㄹ' to 'ㅎ'
        'ㅄ' -> 'ㅂ' to 'ㅅ'
        else -> null
    }

    data class ComposerResult(
        val commit: String = "",
        val update: String = "",
    )

    companion object {
        private const val HANGUL_BASE = 0xAC00

        private val INITIAL_INDEX = mapOf(
            'ㄱ' to 0, 'ㄲ' to 1, 'ㄴ' to 2, 'ㄷ' to 3, 'ㄸ' to 4, 'ㄹ' to 5,
            'ㅁ' to 6, 'ㅂ' to 7, 'ㅃ' to 8, 'ㅅ' to 9, 'ㅆ' to 10, 'ㅇ' to 11,
            'ㅈ' to 12, 'ㅉ' to 13, 'ㅊ' to 14, 'ㅋ' to 15, 'ㅌ' to 16, 'ㅍ' to 17, 'ㅎ' to 18,
        )

        private val MEDIAL_INDEX = mapOf(
            'ㅏ' to 0, 'ㅐ' to 1, 'ㅑ' to 2, 'ㅒ' to 3, 'ㅓ' to 4, 'ㅔ' to 5,
            'ㅕ' to 6, 'ㅖ' to 7, 'ㅗ' to 8, 'ㅘ' to 9, 'ㅙ' to 10, 'ㅚ' to 11,
            'ㅛ' to 12, 'ㅜ' to 13, 'ㅝ' to 14, 'ㅞ' to 15, 'ㅟ' to 16, 'ㅠ' to 17,
            'ㅡ' to 18, 'ㅢ' to 19, 'ㅣ' to 20,
        )

        private val FINAL_INDEX = mapOf(
            'ㄱ' to 1, 'ㄲ' to 2, 'ㄳ' to 3, 'ㄴ' to 4, 'ㄵ' to 5, 'ㄶ' to 6,
            'ㄷ' to 7, 'ㄹ' to 8, 'ㄺ' to 9, 'ㄻ' to 10, 'ㄼ' to 11, 'ㄽ' to 12,
            'ㄾ' to 13, 'ㄿ' to 14, 'ㅀ' to 15, 'ㅁ' to 16, 'ㅂ' to 17, 'ㅄ' to 18,
            'ㅅ' to 19, 'ㅆ' to 20, 'ㅇ' to 21, 'ㅈ' to 22, 'ㅊ' to 23, 'ㅋ' to 24,
            'ㅌ' to 25, 'ㅍ' to 26, 'ㅎ' to 27,
        )
    }
}
