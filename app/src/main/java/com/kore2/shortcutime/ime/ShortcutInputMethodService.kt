package com.kore2.shortcutime.ime

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemePalette
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.ShortcutMatch

class ShortcutInputMethodService : InputMethodService() {
    private lateinit var repository: FolderRepository
    private lateinit var themeStore: KeyboardThemeStore
    private lateinit var previewView: ColemakPreviewView
    private lateinit var previewTitle: TextView
    private lateinit var previewStatus: TextView
    private lateinit var candidateTitle: TextView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var previewPanel: View
    private lateinit var previewRestoreRow: View
    private lateinit var previewRestoreButton: Button
    private lateinit var keyboardAreaContainer: View
    private lateinit var themeOptionsRow: View
    private lateinit var previewModeButton: Button
    private lateinit var themeToggleButton: Button
    private lateinit var openAppButton: Button
    private var keyboardRootView: View? = null
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null
    private val shortcutBuffer = StringBuilder()
    private val keyButtons = linkedMapOf<Int, Button>()
    private val hangulComposer = HangulComposer()
    private var isUppercase = false
    private var keyboardMode = KeyboardMode.LETTERS
    private var languageMode = LanguageMode.ENGLISH
    private var symbolPage = SymbolPage.PRIMARY
    private var previewMode = PreviewMode.SEMI
    private var themeOptionsVisible = false
    private var currentCandidates: List<ShortcutMatch> = emptyList()

    override fun onCreate() {
        super.onCreate()
        repository = FolderRepository(this)
        themeStore = KeyboardThemeStore(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetInputState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetInputState()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.ime_keyboard, null)
        keyboardRootView = root
        previewView = root.findViewById(R.id.colemakPreview)
        previewTitle = root.findViewById(R.id.previewTitle)
        previewStatus = root.findViewById(R.id.previewStatus)
        candidateTitle = root.findViewById(R.id.candidateTitle)
        candidateContainer = root.findViewById(R.id.candidateContainer)
        candidateScroll = root.findViewById(R.id.candidateScroll)
        previewPanel = root.findViewById(R.id.previewPanel)
        previewRestoreRow = root.findViewById(R.id.previewRestoreRow)
        previewRestoreButton = root.findViewById(R.id.previewRestoreButton)
        keyboardAreaContainer = root.findViewById(R.id.keyboardAreaContainer)
        themeOptionsRow = root.findViewById(R.id.themeOptionsRow)
        previewModeButton = root.findViewById(R.id.previewModeButton)
        themeToggleButton = root.findViewById(R.id.themeToggleButton)
        openAppButton = root.findViewById(R.id.openAppButton)

        bindCharacterKeys(root)
        bindThemeOptions(root)

        root.findViewById<Button>(R.id.keyShift).setOnClickListener {
            if (keyboardMode == KeyboardMode.LETTERS) {
                isUppercase = !isUppercase
            } else {
                symbolPage = if (symbolPage == SymbolPage.PRIMARY) SymbolPage.SECONDARY else SymbolPage.PRIMARY
            }
            refreshKeyboardLabels(root)
        }
        root.findViewById<Button>(R.id.keyLanguage).setOnClickListener {
            flushHangulComposition()
            languageMode = if (languageMode == LanguageMode.ENGLISH) {
                LanguageMode.KOREAN
            } else {
                LanguageMode.ENGLISH
            }
            keyboardMode = KeyboardMode.LETTERS
            isUppercase = false
            symbolPage = SymbolPage.PRIMARY
            shortcutBuffer.clear()
            refreshKeyboardLabels(root)
            updatePreview()
        }
        root.findViewById<Button>(R.id.keyMode).setOnClickListener {
            flushHangulComposition()
            keyboardMode = if (keyboardMode == KeyboardMode.LETTERS) {
                KeyboardMode.SYMBOLS
            } else {
                KeyboardMode.LETTERS
            }
            isUppercase = false
            symbolPage = SymbolPage.PRIMARY
            refreshKeyboardLabels(root)
            updatePreview()
        }
        val backspaceBtn = root.findViewById<Button>(R.id.keyBackspace)
        backspaceBtn.setOnClickListener { handleBackspace() }
        backspaceBtn.setOnLongClickListener {
            val runnable = object : Runnable {
                override fun run() {
                    handleBackspace()
                    backspaceHandler.postDelayed(this, 50L)
                }
            }
            backspaceRunnable = runnable
            backspaceHandler.post(runnable)
            true
        }
        backspaceBtn.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                backspaceRunnable = null
            }
            false
        }
        root.findViewById<Button>(R.id.keySpace).setOnClickListener {
            flushHangulComposition()
            commitCurrentBufferWithExpansion(" ")
        }
        root.findViewById<Button>(R.id.keyEnter).setOnClickListener {
            flushHangulComposition()
            handleEnterKey()
        }
        openAppButton.setOnClickListener {
            openShortcutApp()
        }
        previewModeButton.setOnClickListener {
            previewMode = when (previewMode) {
                PreviewMode.ON -> PreviewMode.SEMI
                PreviewMode.SEMI -> PreviewMode.OFF
                PreviewMode.OFF -> PreviewMode.ON
            }
            if (previewMode == PreviewMode.OFF) {
                themeOptionsVisible = false
            }
            refreshKeyboardLabels(root)
            updatePreview()
        }
        themeToggleButton.setOnClickListener {
            themeOptionsVisible = !themeOptionsVisible
            updateThemeOptionsVisibility()
        }
        previewRestoreButton.setOnClickListener {
            previewMode = PreviewMode.ON
            refreshKeyboardLabels(root)
            updatePreview()
        }

        refreshKeyboardLabels(root)
        applyCurrentTheme(root)
        updatePreview()
        return root
    }

    private fun bindCharacterKeys(root: View) {
        CHARACTER_KEY_IDS.forEach { id ->
            val button = root.findViewById<Button>(id)
            keyButtons[id] = button
            button.setOnClickListener {
                handleCharacterPress(id)
            }
        }
    }

    private fun bindThemeOptions(root: View) {
        val buttons = listOf(
            root.findViewById<Button>(R.id.themeOption1),
            root.findViewById<Button>(R.id.themeOption2),
            root.findViewById<Button>(R.id.themeOption3),
            root.findViewById<Button>(R.id.themeOption4),
            root.findViewById<Button>(R.id.themeOption5),
            root.findViewById<Button>(R.id.themeOption6),
            root.findViewById<Button>(R.id.themeOption7),
            root.findViewById<Button>(R.id.themeOption8),
            root.findViewById<Button>(R.id.themeOption9),
            root.findViewById<Button>(R.id.themeOption10),
            root.findViewById<Button>(R.id.themeOption11),
            root.findViewById<Button>(R.id.themeOption12),
        )
        themeStore.selectableThemes().zip(buttons).forEach { (theme, button) ->
            button.background = swatchCircleDrawable(theme)
            button.setOnClickListener {
                themeStore.setTheme(theme.id)
                themeOptionsVisible = false
                keyboardRootView?.let(::applyCurrentTheme)
            }
        }
    }

    private fun handleCharacterPress(id: Int) {
        val baseChar = currentCharacterMap()[id] ?: return
        val activeChar = if (keyboardMode == KeyboardMode.LETTERS && isUppercase) {
            currentShiftCharacterMap()[id] ?: baseChar
        } else {
            baseChar
        }

        if (languageMode == LanguageMode.KOREAN && keyboardMode == KeyboardMode.LETTERS) {
            handleHangulPress(activeChar)
            if (isUppercase) {
                isUppercase = false
                keyboardRootView?.let { refreshKeyboardLabels(it) }
            }
            return
        }

        val committedChar = if (languageMode == LanguageMode.ENGLISH && keyboardMode == KeyboardMode.LETTERS && isUppercase) {
            activeChar.uppercaseChar()
        } else {
            activeChar
        }

        currentInputConnection?.commitText(committedChar.toString(), 1)
        if (languageMode == LanguageMode.ENGLISH && keyboardMode == KeyboardMode.LETTERS) {
            shortcutBuffer.append(committedChar)
        }

        if (keyboardMode == KeyboardMode.LETTERS && isUppercase) {
            isUppercase = false
            keyboardRootView?.let { refreshKeyboardLabels(it) }
        }

        updatePreview()
    }

    private fun handleHangulPress(jamo: Char) {
        val connection = currentInputConnection ?: return
        if (jamo !in HANGUL_JAMOS) {
            flushHangulComposition()
            connection.commitText(jamo.toString(), 1)
            return
        }
        val result = hangulComposer.input(jamo)
        if (result.commit.isNotEmpty()) {
            connection.commitText(result.commit, 1)
        }
        if (result.update.isNotEmpty()) {
            connection.setComposingText(result.update, 1)
        }
        updatePreview()
    }

    private fun applyCurrentTheme(root: View) {
        val theme = themeStore.currentTheme()
        root.setBackgroundColor(theme.appBackground)
        previewPanel.background = roundedRect(theme.previewBackground, theme.strokeColor, 18f)
        keyboardAreaContainer.setBackgroundColor(theme.keyboardBackground)
        previewTitle.setTextColor(theme.textPrimary)
        previewStatus.setTextColor(theme.textSecondary)
        candidateTitle.setTextColor(theme.textPrimary)
        previewView.applyTheme(theme)

        keyButtons.values.forEach { button ->
            button.background = roundedRect(theme.keyBackground, theme.strokeColor, 12f)
            button.setTextColor(theme.textPrimary)
        }

        listOf(
            root.findViewById<Button>(R.id.keyShift),
            root.findViewById<Button>(R.id.keyLanguage),
            root.findViewById<Button>(R.id.keyMode),
            root.findViewById<Button>(R.id.keySpace),
            root.findViewById<Button>(R.id.keyPeriod),
            root.findViewById<Button>(R.id.keyEnter),
            root.findViewById<Button>(R.id.keyBackspace),
            previewModeButton,
            previewRestoreButton,
            openAppButton,
        ).forEach { button ->
            button.background = roundedRect(theme.keyBackground, theme.strokeColor, 12f)
            button.setTextColor(theme.textPrimary)
        }

        themeToggleButton.background = circleDrawable(theme.keyBackground, theme.strokeColor)
        themeToggleButton.setTextColor(theme.textPrimary)
        renderCandidates(currentCandidates)
        updateThemeOptionsVisibility()
    }

    private fun updateThemeOptionsVisibility() {
        themeOptionsRow.visibility = if (themeOptionsVisible && previewPanel.visibility == View.VISIBLE) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun flushHangulComposition() {
        val connection = currentInputConnection ?: return
        if (hangulComposer.hasComposition()) {
            connection.finishComposingText()
            hangulComposer.clear()
        }
    }

    private fun refreshKeyboardLabels(root: View) {
        val modeButton = root.findViewById<Button>(R.id.keyMode)
        val shiftButton = root.findViewById<Button>(R.id.keyShift)
        val languageButton = root.findViewById<Button>(R.id.keyLanguage)
        val charMap = currentCharacterMap()
        val shiftMap = currentShiftCharacterMap()

        keyButtons.forEach { (id, button) ->
            val shouldHide = id !in currentVisibleCharacterKeys()
            button.visibility = if (shouldHide) View.GONE else View.VISIBLE
            if (shouldHide) return@forEach
            val rawChar = charMap[id] ?: return@forEach
            button.text = when {
                keyboardMode == KeyboardMode.LETTERS && isUppercase -> {
                    val shifted = shiftMap[id] ?: rawChar
                    if (languageMode == LanguageMode.ENGLISH) shifted.uppercaseChar().toString() else shifted.toString()
                }
                else -> rawChar.toString()
            }
        }

        languageButton.text = getString(
            if (languageMode == LanguageMode.ENGLISH) {
                R.string.keyboard_lang_english
            } else {
                R.string.keyboard_lang_korean
            },
        )
        if (::previewPanel.isInitialized) {
            val previewAllowed = languageMode == LanguageMode.ENGLISH &&
                keyboardMode == KeyboardMode.LETTERS &&
                previewMode != PreviewMode.OFF
            previewPanel.visibility = if (previewAllowed) View.VISIBLE else View.GONE
            previewRestoreRow.visibility = if (
                languageMode == LanguageMode.ENGLISH &&
                keyboardMode == KeyboardMode.LETTERS &&
                previewMode == PreviewMode.OFF
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            previewModeButton.text = when (previewMode) {
                PreviewMode.ON -> "ON"
                PreviewMode.SEMI -> "LINK"
                PreviewMode.OFF -> "OFF"
            }
            updateThemeOptionsVisibility()
        }
        modeButton.text = getString(if (keyboardMode == KeyboardMode.LETTERS) R.string.keyboard_mode_numbers else R.string.keyboard_mode_letters)
        if (keyboardMode == KeyboardMode.SYMBOLS) {
            shiftButton.text = getString(
                if (symbolPage == SymbolPage.PRIMARY) R.string.keyboard_symbol_primary else R.string.keyboard_symbol_secondary,
            )
            shiftButton.alpha = 1f
        } else {
            shiftButton.text = getString(R.string.keyboard_shift)
            shiftButton.alpha = if (isUppercase) 1f else 0.72f
        }
    }

    private fun currentCharacterMap(): Map<Int, Char> {
        return when {
            keyboardMode == KeyboardMode.SYMBOLS && symbolPage == SymbolPage.SECONDARY -> SYMBOL_SECONDARY_CHARACTER_MAP
            keyboardMode == KeyboardMode.SYMBOLS -> SYMBOL_PRIMARY_CHARACTER_MAP
            languageMode == LanguageMode.KOREAN -> KOREAN_CHARACTER_MAP
            else -> ENGLISH_CHARACTER_MAP
        }
    }

    private fun currentShiftCharacterMap(): Map<Int, Char> {
        return when {
            keyboardMode != KeyboardMode.LETTERS -> emptyMap()
            languageMode == LanguageMode.KOREAN -> KOREAN_SHIFT_CHARACTER_MAP
            else -> ENGLISH_SHIFT_CHARACTER_MAP
        }
    }

    private fun handleBackspace() {
        val connection = currentInputConnection ?: return

        if (languageMode == LanguageMode.KOREAN && keyboardMode == KeyboardMode.LETTERS && hangulComposer.hasComposition()) {
            if (hangulComposer.backspace()) {
                val composing = hangulComposer.currentText()
                if (composing.isNotEmpty()) {
                    connection.setComposingText(composing, 1)
                } else {
                    connection.finishComposingText()
                }
                updatePreview()
                return
            }
        }

        if (shortcutBuffer.isNotEmpty()) {
            shortcutBuffer.deleteCharAt(shortcutBuffer.lastIndex)
        }
        connection.finishComposingText()
        connection.deleteSurroundingText(1, 0)
        updatePreview()
    }

    private fun commitCurrentBufferWithExpansion(delimiter: String) {
        val connection = currentInputConnection ?: return

        if (languageMode != LanguageMode.ENGLISH || keyboardMode != KeyboardMode.LETTERS) {
            connection.commitText(delimiter, 1)
            shortcutBuffer.clear()
            updatePreview()
            return
        }

        val token = shortcutBuffer.toString()
        val match = repository.findMatchingShortcut(token)

        if (match != null) {
            val folder = match.folder
            val item = match.entry
            connection.deleteSurroundingText(token.length, 0)
            connection.commitText(item.expandsTo + delimiter, 1)
            repository.incrementUsage(folder.id, item.id)
            previewStatus.text = getString(R.string.preview_exact, item.expandsTo)
        } else {
            connection.commitText(delimiter, 1)
            previewStatus.text = if (token.isBlank()) {
                getString(R.string.preview_idle)
            } else {
                getString(R.string.preview_typed, token)
            }
        }

        shortcutBuffer.clear()
        previewView.setSequence("")
        renderCandidates(emptyList())
        resetVisualToggles()
    }

    private fun handleEnterKey() {
        val connection = currentInputConnection ?: return

        if (languageMode == LanguageMode.ENGLISH && keyboardMode == KeyboardMode.LETTERS) {
            val token = shortcutBuffer.toString()
            val match = repository.findMatchingShortcut(token)
            if (match != null) {
                val folder = match.folder
                val item = match.entry
                connection.deleteSurroundingText(token.length, 0)
                connection.commitText(item.expandsTo, 1)
                repository.incrementUsage(folder.id, item.id)
                previewStatus.text = getString(R.string.preview_exact, item.expandsTo)
            }
            shortcutBuffer.clear()
            previewView.setSequence("")
            renderCandidates(emptyList())
            resetVisualToggles()
        } else {
            updatePreview()
        }

        val editorInfo = currentInputEditorInfo
        val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        val performed = when (actionId) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS -> currentInputConnection?.performEditorAction(actionId) == true
            else -> false
        }

        if (!performed) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun updatePreview() {
        when {
            languageMode == LanguageMode.KOREAN && keyboardMode == KeyboardMode.LETTERS -> {
                previewView.clearAnimationState()
                previewStatus.text = if (hangulComposer.hasComposition()) {
                    hangulComposer.currentText()
                } else {
                    getString(R.string.preview_idle)
                }
                renderCandidates(emptyList())
            }
            languageMode == LanguageMode.ENGLISH && keyboardMode == KeyboardMode.LETTERS -> {
                val current = shortcutBuffer.toString()
                val exactMatch = repository.findMatchingShortcut(current)
                val candidates = repository.findMatchingCandidates(current)
                val candidate = candidates.firstOrNull()
                when (previewMode) {
                    PreviewMode.ON -> previewView.setSequence(current)
                    PreviewMode.SEMI -> {
                        if (exactMatch != null) {
                            previewView.playSequence(current)
                        } else {
                            previewView.clearAnimationState()
                        }
                    }
                    PreviewMode.OFF -> previewView.clearAnimationState()
                }
                previewStatus.text = when {
                    current.isBlank() -> getString(R.string.preview_idle)
                    exactMatch != null -> getString(R.string.preview_exact, exactMatch.entry.expandsTo)
                    candidate != null -> getString(R.string.preview_candidate, candidate.entry.expandsTo)
                    else -> getString(R.string.preview_typed, current)
                }
                renderCandidates(candidates)
            }
            else -> {
                previewView.clearAnimationState()
                previewStatus.text = getString(R.string.preview_idle)
                renderCandidates(emptyList())
            }
        }
    }

    private fun renderCandidates(items: List<ShortcutMatch>) {
        if (!::candidateContainer.isInitialized) return
        currentCandidates = items
        candidateContainer.removeAllViews()

        if (items.isEmpty()) {
            val emptyChip = buildCandidateChip(
                text = getString(R.string.candidate_none),
                enabled = false,
                onClick = null,
            )
            candidateContainer.addView(emptyChip)
            return
        }

        items.forEachIndexed { index, match ->
            val chip = buildCandidateChip(
                text = getString(R.string.candidate_with_folder, match.entry.expandsTo, match.folder.title),
                enabled = true,
                onClick = { commitCandidate(match) },
            )
            (chip.layoutParams as LinearLayout.LayoutParams).marginEnd = if (index == items.lastIndex) 0 else 12
            candidateContainer.addView(chip)
        }
        candidateScroll.post { candidateScroll.scrollTo(0, 0) }
    }

    private fun buildCandidateChip(
        text: String,
        enabled: Boolean,
        onClick: (() -> Unit)?,
    ): AppCompatButton {
        val theme = themeStore.currentTheme()
        return AppCompatButton(this).apply {
            this.text = text
            isEnabled = enabled
            setTextColor(if (enabled) theme.textPrimary else theme.textSecondary)
            background = roundedRect(theme.keyBackground, theme.strokeColor, 12f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(28, 16, 28, 16)
            if (onClick != null) {
                setOnClickListener { onClick() }
            }
        }
    }

    private fun commitCandidate(match: ShortcutMatch) {
        val connection = currentInputConnection ?: return
        flushHangulComposition()
        val token = shortcutBuffer.toString()
        if (token.isNotBlank()) {
            connection.deleteSurroundingText(token.length, 0)
        }
        connection.commitText(match.entry.expandsTo, 1)
        repository.incrementUsage(match.folder.id, match.entry.id)
        shortcutBuffer.clear()
        previewView.setSequence(match.entry.shortcut)
        previewStatus.text = getString(R.string.preview_exact, match.entry.expandsTo)
        renderCandidates(emptyList())
    }

    private fun resetVisualToggles() {
        isUppercase = false
        keyboardMode = KeyboardMode.LETTERS
        symbolPage = SymbolPage.PRIMARY
        keyboardRootView?.let { refreshKeyboardLabels(it) }
    }

    private fun resetInputState() {
        shortcutBuffer.clear()
        hangulComposer.clear()
        if (::previewView.isInitialized) {
            previewView.clearAnimationState()
        }
        if (::previewStatus.isInitialized) {
            previewStatus.text = getString(R.string.preview_idle)
        }
        if (::candidateContainer.isInitialized) {
            renderCandidates(emptyList())
        }
        isUppercase = false
        keyboardMode = KeyboardMode.LETTERS
        symbolPage = SymbolPage.PRIMARY
        keyboardRootView?.let { refreshKeyboardLabels(it) }
    }

    private fun openShortcutApp() {
        val intent = Intent(this, com.kore2.shortcutime.ui.HostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun roundedRect(fillColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(fillColor)
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    private fun circleDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    private fun swatchCircleDrawable(theme: KeyboardThemePalette): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (theme.swatchMiddleColor != null) {
                intArrayOf(
                    theme.swatchTopColor,
                    theme.swatchTopColor,
                    theme.swatchMiddleColor,
                    theme.swatchMiddleColor,
                    theme.swatchBottomColor,
                    theme.swatchBottomColor,
                )
            } else if (theme.isSplitSwatch) {
                intArrayOf(
                    theme.swatchTopColor,
                    theme.swatchTopColor,
                    theme.swatchBottomColor,
                    theme.swatchBottomColor,
                )
            } else {
                intArrayOf(theme.swatchTopColor, theme.swatchTopColor)
            },
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), theme.strokeColor)
        }
    }

    private fun currentVisibleCharacterKeys(): Set<Int> {
        return when {
            keyboardMode == KeyboardMode.SYMBOLS -> SYMBOL_VISIBLE_KEY_IDS
            languageMode == LanguageMode.KOREAN -> KOREAN_VISIBLE_KEY_IDS
            else -> ENGLISH_VISIBLE_KEY_IDS
        }
    }

    private enum class KeyboardMode {
        LETTERS,
        SYMBOLS,
    }

    private enum class SymbolPage {
        PRIMARY,
        SECONDARY,
    }

    private enum class PreviewMode {
        ON,
        SEMI,
        OFF,
    }

    private enum class LanguageMode {
        ENGLISH,
        KOREAN,
    }

    companion object {
        private val CHARACTER_KEY_IDS = listOf(
            R.id.keyQ,
            R.id.keyDigit1,
            R.id.keyDigit2,
            R.id.keyDigit3,
            R.id.keyDigit4,
            R.id.keyDigit5,
            R.id.keyDigit6,
            R.id.keyDigit7,
            R.id.keyDigit8,
            R.id.keyDigit9,
            R.id.keyDigit0,
            R.id.keyW,
            R.id.keyF,
            R.id.keyP,
            R.id.keyG,
            R.id.keyJ,
            R.id.keyL,
            R.id.keyU,
            R.id.keyY,
            R.id.keySlash,
            R.id.keyLeftBracket,
            R.id.keyRightBracket,
            R.id.keyA,
            R.id.keyR,
            R.id.keyS,
            R.id.keyT,
            R.id.keyD,
            R.id.keyH,
            R.id.keyN,
            R.id.keyE,
            R.id.keyI,
            R.id.keyO,
            R.id.keyApostrophe,
            R.id.keyZ,
            R.id.keyX,
            R.id.keyC,
            R.id.keyV,
            R.id.keyB,
            R.id.keyK,
            R.id.keyM,
            R.id.keyComma,
            R.id.keyPeriod,
            R.id.keyForwardSlash,
        )

        private val ENGLISH_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '1',
            R.id.keyDigit2 to '2',
            R.id.keyDigit3 to '3',
            R.id.keyDigit4 to '4',
            R.id.keyDigit5 to '5',
            R.id.keyDigit6 to '6',
            R.id.keyDigit7 to '7',
            R.id.keyDigit8 to '8',
            R.id.keyDigit9 to '9',
            R.id.keyDigit0 to '0',
            R.id.keyQ to 'q',
            R.id.keyW to 'w',
            R.id.keyF to 'f',
            R.id.keyP to 'p',
            R.id.keyG to 'g',
            R.id.keyJ to 'j',
            R.id.keyL to 'l',
            R.id.keyU to 'u',
            R.id.keyY to 'y',
            R.id.keySlash to '\'',
            R.id.keyLeftBracket to '[',
            R.id.keyRightBracket to ']',
            R.id.keyA to 'a',
            R.id.keyR to 'r',
            R.id.keyS to 's',
            R.id.keyT to 't',
            R.id.keyD to 'd',
            R.id.keyH to 'h',
            R.id.keyN to 'n',
            R.id.keyE to 'e',
            R.id.keyI to 'i',
            R.id.keyO to 'o',
            R.id.keyApostrophe to '\'',
            R.id.keyZ to 'z',
            R.id.keyX to 'x',
            R.id.keyC to 'c',
            R.id.keyV to 'v',
            R.id.keyB to 'b',
            R.id.keyK to 'k',
            R.id.keyM to 'm',
            R.id.keyComma to ',',
            R.id.keyPeriod to '.',
            R.id.keyForwardSlash to '/',
        )

        private val KOREAN_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '1',
            R.id.keyDigit2 to '2',
            R.id.keyDigit3 to '3',
            R.id.keyDigit4 to '4',
            R.id.keyDigit5 to '5',
            R.id.keyDigit6 to '6',
            R.id.keyDigit7 to '7',
            R.id.keyDigit8 to '8',
            R.id.keyDigit9 to '9',
            R.id.keyDigit0 to '0',
            R.id.keyQ to 'ㅂ',
            R.id.keyW to 'ㅈ',
            R.id.keyF to 'ㄷ',
            R.id.keyP to 'ㄱ',
            R.id.keyG to 'ㅅ',
            R.id.keyJ to 'ㅛ',
            R.id.keyL to 'ㅕ',
            R.id.keyU to 'ㅑ',
            R.id.keyY to 'ㅐ',
            R.id.keySlash to 'ㅔ',
            R.id.keyLeftBracket to '[',
            R.id.keyRightBracket to ']',
            R.id.keyA to 'ㅁ',
            R.id.keyR to 'ㄴ',
            R.id.keyS to 'ㅇ',
            R.id.keyT to 'ㄹ',
            R.id.keyD to 'ㅎ',
            R.id.keyH to 'ㅗ',
            R.id.keyN to 'ㅓ',
            R.id.keyE to 'ㅏ',
            R.id.keyI to 'ㅣ',
            R.id.keyO to ';',
            R.id.keyApostrophe to '\'',
            R.id.keyZ to 'ㅋ',
            R.id.keyX to 'ㅌ',
            R.id.keyC to 'ㅊ',
            R.id.keyV to 'ㅍ',
            R.id.keyB to 'ㅠ',
            R.id.keyK to 'ㅜ',
            R.id.keyM to 'ㅡ',
            R.id.keyPeriod to '.',
            R.id.keyForwardSlash to '?',
        )

        private val SYMBOL_PRIMARY_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '1',
            R.id.keyDigit2 to '2',
            R.id.keyDigit3 to '3',
            R.id.keyDigit4 to '4',
            R.id.keyDigit5 to '5',
            R.id.keyDigit6 to '6',
            R.id.keyDigit7 to '7',
            R.id.keyDigit8 to '8',
            R.id.keyDigit9 to '9',
            R.id.keyDigit0 to '0',
            R.id.keyQ to '%',
            R.id.keyW to '₩',
            R.id.keyF to '=',
            R.id.keyP to '&',
            R.id.keyG to '·',
            R.id.keyJ to '*',
            R.id.keyL to '-',
            R.id.keyU to '+',
            R.id.keyY to '<',
            R.id.keySlash to '>',
            R.id.keyLeftBracket to '<',
            R.id.keyRightBracket to '>',
            R.id.keyA to '@',
            R.id.keyR to '#',
            R.id.keyS to ':',
            R.id.keyT to ';',
            R.id.keyD to '^',
            R.id.keyH to '♡',
            R.id.keyN to '_',
            R.id.keyE to '/',
            R.id.keyI to '(',
            R.id.keyO to ')',
            R.id.keyZ to '\'',
            R.id.keyX to '"',
            R.id.keyC to '~',
            R.id.keyV to '.',
            R.id.keyB to ',',
            R.id.keyK to '!',
            R.id.keyM to '?',
            R.id.keyComma to '…',
            R.id.keyPeriod to '.',
            R.id.keyForwardSlash to '!',
        )

        private val SYMBOL_SECONDARY_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '1',
            R.id.keyDigit2 to '2',
            R.id.keyDigit3 to '3',
            R.id.keyDigit4 to '4',
            R.id.keyDigit5 to '5',
            R.id.keyDigit6 to '6',
            R.id.keyDigit7 to '7',
            R.id.keyDigit8 to '8',
            R.id.keyDigit9 to '9',
            R.id.keyDigit0 to '0',
            R.id.keyQ to '≠',
            R.id.keyW to '÷',
            R.id.keyF to '×',
            R.id.keyP to '$',
            R.id.keyG to '¥',
            R.id.keyJ to '|',
            R.id.keyL to '\\',
            R.id.keyU to '{',
            R.id.keyY to '}',
            R.id.keySlash to '○',
            R.id.keyLeftBracket to '◇',
            R.id.keyRightBracket to '◆',
            R.id.keyA to '●',
            R.id.keyR to '□',
            R.id.keyS to '■',
            R.id.keyT to '※',
            R.id.keyD to '♥',
            R.id.keyH to '☆',
            R.id.keyN to '★',
            R.id.keyE to '[',
            R.id.keyI to ']',
            R.id.keyO to '←',
            R.id.keyZ to '↑',
            R.id.keyX to '↓',
            R.id.keyC to '→',
            R.id.keyV to '↔',
            R.id.keyB to '《',
            R.id.keyK to '》',
            R.id.keyM to '•',
            R.id.keyComma to '.',
            R.id.keyPeriod to '.',
            R.id.keyForwardSlash to '·',
        )

        private val ENGLISH_SHIFT_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '!',
            R.id.keyDigit2 to '@',
            R.id.keyDigit3 to '#',
            R.id.keyDigit4 to '$',
            R.id.keyDigit5 to '%',
            R.id.keyDigit6 to '^',
            R.id.keyDigit7 to '&',
            R.id.keyDigit8 to '*',
            R.id.keyDigit9 to '(',
            R.id.keyDigit0 to ')',
            R.id.keyQ to 'q',
            R.id.keyW to 'w',
            R.id.keyF to 'f',
            R.id.keyP to 'p',
            R.id.keyG to 'g',
            R.id.keyJ to 'j',
            R.id.keyL to 'l',
            R.id.keyU to 'u',
            R.id.keyY to 'y',
            R.id.keySlash to '"',
            R.id.keyLeftBracket to '{',
            R.id.keyRightBracket to '}',
            R.id.keyA to 'a',
            R.id.keyR to 'r',
            R.id.keyS to 's',
            R.id.keyT to 't',
            R.id.keyD to 'd',
            R.id.keyH to 'h',
            R.id.keyN to 'n',
            R.id.keyE to 'e',
            R.id.keyI to 'i',
            R.id.keyO to 'o',
            R.id.keyApostrophe to '"',
            R.id.keyZ to 'z',
            R.id.keyX to 'x',
            R.id.keyC to 'c',
            R.id.keyV to 'v',
            R.id.keyB to 'b',
            R.id.keyK to 'k',
            R.id.keyM to 'm',
            R.id.keyComma to '<',
            R.id.keyPeriod to '>',
            R.id.keyForwardSlash to '?',
        )

        private val KOREAN_SHIFT_CHARACTER_MAP = linkedMapOf(
            R.id.keyDigit1 to '1',
            R.id.keyDigit2 to '2',
            R.id.keyDigit3 to '3',
            R.id.keyDigit4 to '4',
            R.id.keyDigit5 to '5',
            R.id.keyDigit6 to '6',
            R.id.keyDigit7 to '7',
            R.id.keyDigit8 to '8',
            R.id.keyDigit9 to '9',
            R.id.keyDigit0 to '0',
            R.id.keyQ to 'ㅃ',
            R.id.keyW to 'ㅉ',
            R.id.keyF to 'ㄸ',
            R.id.keyP to 'ㄲ',
            R.id.keyG to 'ㅆ',
            R.id.keyJ to 'ㅛ',
            R.id.keyL to 'ㅕ',
            R.id.keyU to 'ㅑ',
            R.id.keyY to 'ㅒ',
            R.id.keySlash to 'ㅖ',
            R.id.keyLeftBracket to '{',
            R.id.keyRightBracket to '}',
            R.id.keyA to 'ㅁ',
            R.id.keyR to 'ㄴ',
            R.id.keyS to 'ㅇ',
            R.id.keyT to 'ㄹ',
            R.id.keyD to 'ㅎ',
            R.id.keyH to 'ㅗ',
            R.id.keyN to 'ㅓ',
            R.id.keyE to 'ㅏ',
            R.id.keyI to 'ㅣ',
            R.id.keyO to ':',
            R.id.keyApostrophe to '"',
            R.id.keyZ to 'ㅋ',
            R.id.keyX to 'ㅌ',
            R.id.keyC to 'ㅊ',
            R.id.keyV to 'ㅍ',
            R.id.keyB to 'ㅠ',
            R.id.keyK to 'ㅜ',
            R.id.keyM to 'ㅡ',
            R.id.keyPeriod to '>',
            R.id.keyForwardSlash to '?',
        )

        private val HANGUL_JAMOS = setOf(
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅛ', 'ㅜ', 'ㅠ', 'ㅡ', 'ㅣ',
        )

        private val ENGLISH_VISIBLE_KEY_IDS = setOf(
            R.id.keyDigit1,
            R.id.keyDigit2,
            R.id.keyDigit3,
            R.id.keyDigit4,
            R.id.keyDigit5,
            R.id.keyDigit6,
            R.id.keyDigit7,
            R.id.keyDigit8,
            R.id.keyDigit9,
            R.id.keyDigit0,
            R.id.keyQ,
            R.id.keyW,
            R.id.keyF,
            R.id.keyP,
            R.id.keyG,
            R.id.keyJ,
            R.id.keyL,
            R.id.keyU,
            R.id.keyY,
            R.id.keySlash,
            R.id.keyA,
            R.id.keyR,
            R.id.keyS,
            R.id.keyT,
            R.id.keyD,
            R.id.keyH,
            R.id.keyN,
            R.id.keyE,
            R.id.keyI,
            R.id.keyO,
            R.id.keyZ,
            R.id.keyX,
            R.id.keyC,
            R.id.keyV,
            R.id.keyB,
            R.id.keyK,
            R.id.keyM,
            R.id.keyComma,
            R.id.keyPeriod,
        )

        private val KOREAN_VISIBLE_KEY_IDS = setOf(
            R.id.keyDigit1,
            R.id.keyDigit2,
            R.id.keyDigit3,
            R.id.keyDigit4,
            R.id.keyDigit5,
            R.id.keyDigit6,
            R.id.keyDigit7,
            R.id.keyDigit8,
            R.id.keyDigit9,
            R.id.keyDigit0,
            R.id.keyQ,
            R.id.keyW,
            R.id.keyF,
            R.id.keyP,
            R.id.keyG,
            R.id.keyJ,
            R.id.keyL,
            R.id.keyU,
            R.id.keyY,
            R.id.keySlash,
            R.id.keyA,
            R.id.keyR,
            R.id.keyS,
            R.id.keyT,
            R.id.keyD,
            R.id.keyH,
            R.id.keyN,
            R.id.keyE,
            R.id.keyI,
            R.id.keyZ,
            R.id.keyX,
            R.id.keyC,
            R.id.keyV,
            R.id.keyB,
            R.id.keyK,
            R.id.keyM,
            R.id.keyPeriod,
        )

        private val SYMBOL_VISIBLE_KEY_IDS = setOf(
            R.id.keyDigit1,
            R.id.keyDigit2,
            R.id.keyDigit3,
            R.id.keyDigit4,
            R.id.keyDigit5,
            R.id.keyDigit6,
            R.id.keyDigit7,
            R.id.keyDigit8,
            R.id.keyDigit9,
            R.id.keyDigit0,
            R.id.keyQ,
            R.id.keyW,
            R.id.keyF,
            R.id.keyP,
            R.id.keyG,
            R.id.keyJ,
            R.id.keyL,
            R.id.keyU,
            R.id.keyY,
            R.id.keySlash,
            R.id.keyA,
            R.id.keyR,
            R.id.keyS,
            R.id.keyT,
            R.id.keyD,
            R.id.keyH,
            R.id.keyN,
            R.id.keyE,
            R.id.keyI,
            R.id.keyO,
            R.id.keyZ,
            R.id.keyX,
            R.id.keyC,
            R.id.keyV,
            R.id.keyB,
            R.id.keyK,
            R.id.keyM,
            R.id.keyComma,
            R.id.keyPeriod,
        )
    }
}
