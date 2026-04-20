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

/**
 * Returns true when [color] is dark enough that white text reads more clearly on it.
 * Uses WCAG 2.1 relative luminance formula.
 */
internal fun isColorDark(color: Int): Boolean {
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
    // Override the toolbar background completely.
    // setBackgroundColor() alone does not remove the XML backgroundTint on MaterialToolbar,
    // so we null it out explicitly — otherwise the XML bg_panel (#23283C) tint persists.
    toolbar.setBackgroundColor(theme.previewBackground)
    toolbar.backgroundTintList = null

    // Choose a legible text/icon color: white on dark backgrounds, theme primary on light ones.
    val contentColor = if (isColorDark(theme.previewBackground)) Color.WHITE else theme.textPrimary

    toolbar.setTitleTextColor(contentColor)

    // mutate() gives us a private drawable state so setTint() won't affect other drawables
    // that share the same constant state. Re-assign so the toolbar picks up the change.
    toolbar.navigationIcon?.mutate()?.let { icon ->
        icon.setTint(contentColor)
        toolbar.navigationIcon = icon
    }
    toolbar.overflowIcon?.mutate()?.let { icon ->
        icon.setTint(contentColor)
        toolbar.overflowIcon = icon
    }
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
