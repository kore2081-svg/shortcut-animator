package com.kore2.shortcutime.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kore2.shortcutime.data.KeyboardThemePalette

internal fun roundedRectDrawable(fillColor: Int, strokeColor: Int, radiusDp: Float, targetView: View): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * targetView.resources.displayMetrics.density
        setColor(fillColor)
        setStroke(targetView.resources.displayMetrics.density.toInt().coerceAtLeast(1), strokeColor)
    }
}

/** Returns true when [color] is dark enough that white text reads more clearly on it. */
private fun isColorDark(color: Int): Boolean {
    // Relative luminance per WCAG 2.1
    fun linearize(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    val l = 0.2126 * linearize(Color.red(color)) +
        0.7152 * linearize(Color.green(color)) +
        0.0722 * linearize(Color.blue(color))
    return l < 0.35
}

internal fun applyToolbarTheme(toolbar: MaterialToolbar, theme: KeyboardThemePalette) {
    toolbar.setBackgroundColor(theme.previewBackground)
    // Use white text on dark backgrounds so navigation icons and titles stay legible
    val textColor = if (isColorDark(theme.previewBackground)) Color.WHITE else theme.textPrimary
    toolbar.setTitleTextColor(textColor)
    toolbar.navigationIcon?.setTint(textColor)
    toolbar.overflowIcon?.setTint(textColor)
}

internal fun applyFilledButtonTheme(button: MaterialButton, theme: KeyboardThemePalette) {
    button.backgroundTintList = ColorStateList.valueOf(theme.keyBackground)
    button.strokeColor = ColorStateList.valueOf(theme.strokeColor)
    button.setTextColor(theme.textPrimary)
    button.iconTint = ColorStateList.valueOf(theme.textPrimary)
}

internal fun applyFabTheme(fab: FloatingActionButton, theme: KeyboardThemePalette) {
    fab.backgroundTintList = ColorStateList.valueOf(theme.accentColor)
    fab.imageTintList = ColorStateList.valueOf(theme.appBackground)
}

internal fun applyInputLayoutTheme(
    layout: TextInputLayout,
    editText: TextInputEditText?,
    theme: KeyboardThemePalette,
) {
    layout.boxBackgroundColor = theme.keyBackground
    layout.boxStrokeColor = theme.strokeColor
    layout.defaultHintTextColor = ColorStateList.valueOf(theme.textSecondary)
    layout.setHintTextColor(ColorStateList.valueOf(theme.textSecondary))
    editText?.setTextColor(theme.textPrimary)
    editText?.setHintTextColor(theme.textSecondary)
}

internal fun applySwitchTheme(toggle: SwitchCompat, theme: KeyboardThemePalette) {
    toggle.setTextColor(theme.textPrimary)
    toggle.thumbTintList = ColorStateList.valueOf(theme.accentColor)
    toggle.trackTintList = ColorStateList.valueOf(theme.keyBackground)
}

internal fun applyBodyTextTheme(textView: TextView, theme: KeyboardThemePalette, emphasize: Boolean = false) {
    textView.setTextColor(if (emphasize) theme.textPrimary else theme.textSecondary)
}
