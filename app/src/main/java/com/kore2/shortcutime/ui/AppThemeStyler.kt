package com.kore2.shortcutime.ui

import android.content.res.ColorStateList
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

internal fun applyToolbarTheme(toolbar: MaterialToolbar, theme: KeyboardThemePalette) {
    toolbar.setBackgroundColor(theme.previewBackground)
    toolbar.setTitleTextColor(theme.textPrimary)
    toolbar.navigationIcon?.setTint(theme.textPrimary)
    toolbar.overflowIcon?.setTint(theme.textPrimary)
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
