package com.kore2.shortcutime.data

import android.content.Context
import android.graphics.Color

data class KeyboardThemePalette(
    val id: Int,
    val swatchTopColor: Int,
    val swatchBottomColor: Int,
    val swatchMiddleColor: Int? = null,
    val appBackground: Int,
    val previewBackground: Int,
    val keyboardBackground: Int,
    val keyBackground: Int,
    val strokeColor: Int,
    val accentColor: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val previewArrow: Int,
) {
    val isSplitSwatch: Boolean = swatchMiddleColor != null || swatchTopColor != swatchBottomColor
}

class KeyboardThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("shortcut_store", Context.MODE_PRIVATE)

    fun currentTheme(): KeyboardThemePalette {
        val selectedId = prefs.getInt(KEY_THEME_ID, DEFAULT_THEME.id)
        return THEMES.firstOrNull { it.id == selectedId } ?: DEFAULT_THEME
    }

    fun setTheme(themeId: Int) {
        prefs.edit().putInt(KEY_THEME_ID, themeId).apply()
    }

    fun selectableThemes(): List<KeyboardThemePalette> = THEMES

    companion object {
        private const val KEY_THEME_ID = "keyboard_theme_id"
        private val DARK_TEXT = Color.parseColor("#263247")
        private val SOFT_TEXT = Color.parseColor("#637188")
        private val BRIGHT_TEXT = Color.parseColor("#F8FBFF")
        private val BRIGHT_SECONDARY = Color.parseColor("#EAF2FF")

        val DEFAULT_THEME = KeyboardThemePalette(
            id = 1,
            swatchTopColor = Color.parseColor("#F6F0DF"),
            swatchBottomColor = Color.parseColor("#F6F0DF"),
            appBackground = mix(Color.parseColor("#F6F0DF"), Color.WHITE, 0.18f),
            previewBackground = Color.parseColor("#F6F0DF"),
            keyboardBackground = Color.parseColor("#F6F0DF"),
            keyBackground = mix(Color.parseColor("#F6F0DF"), Color.WHITE, 0.10f),
            strokeColor = mix(Color.parseColor("#F6F0DF"), Color.BLACK, 0.22f),
            accentColor = mix(Color.parseColor("#F6F0DF"), Color.BLACK, 0.14f),
            textPrimary = DARK_TEXT,
            textSecondary = SOFT_TEXT,
            previewArrow = mix(Color.parseColor("#F6F0DF"), Color.BLACK, 0.34f),
        )

        private val THEMES = listOf(
            DEFAULT_THEME,
            singleTheme(2, "#AFBFDE"),
            singleTheme(3, "#B3DBB3"),
            singleTheme(4, "#D9CFF3"),
            singleTheme(5, "#E8BCC5"),
            splitTheme(6, "#F6F0DF", "#5B698F", previewArrowHex = "#1D4ED8"),
            splitTheme(7, "#98B7E1", "#DCC6A8", previewArrowHex = "#F6B7C8"),
            splitTheme(8, "#B3DBB3", "#CBAECB", previewArrowHex = "#F4D64E"),
            splitTheme(9, "#B07A47", "#2FA3D9", previewArrowHex = "#FFB3C7", textPrimary = BRIGHT_TEXT, textSecondary = BRIGHT_SECONDARY),
            splitTheme(10, "#FFB6C1", "#DC143C", accentHex = "#C6A4FF", previewArrowHex = "#FFF2A8", swatchMiddleHex = "#DC143C", textPrimary = BRIGHT_TEXT, textSecondary = BRIGHT_SECONDARY),
            splitTheme(11, "#FF9F1C", "#B8E26D", previewArrowHex = "#F97316"),
            splitTheme(12, "#00C8F0", "#23272F", previewArrowHex = "#F8FBFF", textPrimary = BRIGHT_TEXT, textSecondary = BRIGHT_SECONDARY),
        )

        private fun singleTheme(
            id: Int,
            baseHex: String,
            textPrimary: Int = DARK_TEXT,
            textSecondary: Int = SOFT_TEXT,
        ): KeyboardThemePalette {
            val base = Color.parseColor(baseHex)
            return KeyboardThemePalette(
                id = id,
                swatchTopColor = base,
                swatchBottomColor = base,
                appBackground = mix(base, Color.WHITE, 0.18f),
                previewBackground = base,
                keyboardBackground = base,
                keyBackground = mix(base, Color.WHITE, 0.10f),
                strokeColor = mix(base, Color.BLACK, 0.22f),
                accentColor = mix(base, Color.BLACK, 0.14f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                previewArrow = mix(base, Color.BLACK, 0.34f),
            )
        }

        private fun splitTheme(
            id: Int,
            previewHex: String,
            keyboardHex: String,
            accentHex: String? = null,
            previewArrowHex: String? = null,
            swatchMiddleHex: String? = null,
            textPrimary: Int = DARK_TEXT,
            textSecondary: Int = SOFT_TEXT,
        ): KeyboardThemePalette {
            val preview = Color.parseColor(previewHex)
            val keyboard = Color.parseColor(keyboardHex)
            val middle = swatchMiddleHex?.let(Color::parseColor)
            val accent = accentHex?.let(Color::parseColor) ?: mix(preview, keyboard, 0.5f)
            val previewArrow = previewArrowHex?.let(Color::parseColor) ?: accent
            return KeyboardThemePalette(
                id = id,
                swatchTopColor = preview,
                swatchBottomColor = keyboard,
                swatchMiddleColor = middle,
                appBackground = mix(preview, Color.WHITE, 0.20f),
                previewBackground = preview,
                keyboardBackground = keyboard,
                keyBackground = mix(keyboard, Color.WHITE, 0.12f),
                strokeColor = mix(keyboard, Color.BLACK, 0.24f),
                accentColor = accent,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                previewArrow = previewArrow,
            )
        }

        private fun mix(start: Int, end: Int, amount: Float): Int {
            val clamped = amount.coerceIn(0f, 1f)
            val inverse = 1f - clamped
            return Color.argb(
                (Color.alpha(start) * inverse + Color.alpha(end) * clamped).toInt(),
                (Color.red(start) * inverse + Color.red(end) * clamped).toInt(),
                (Color.green(start) * inverse + Color.green(end) * clamped).toInt(),
                (Color.blue(start) * inverse + Color.blue(end) * clamped).toInt(),
            )
        }
    }
}
