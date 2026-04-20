package com.kore2.shortcutime.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.kore2.shortcutime.data.KeyboardThemePalette
import kotlin.math.abs
import kotlin.math.min

class ColemakPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#31374F")
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F4FF7A")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3B4465")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#224FA3")
        textAlign = Paint.Align.CENTER
        typeface = textPaint.typeface
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FF9E3D")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keyRects = mutableMapOf<Char, RectF>()
    private val animationHandler = Handler(Looper.getMainLooper())
    private var displayedSequence = emptyList<Char>()
    private var activeIndex = -1
    private var showCompleted = false
    private var showTrail = false
    private var onAnimationFinished: ((String) -> Unit)? = null
    private var animationToken = 0

    fun playSequence(sequence: String) {
        animationToken += 1
        animationHandler.removeCallbacksAndMessages(null)

        displayedSequence = sequence.lowercase().filter { it in COLEMAK_KEYS }.toList()
        activeIndex = -1
        showCompleted = false
        showTrail = false
        invalidate()

        if (displayedSequence.isEmpty()) {
            onAnimationFinished?.invoke("")
            return
        }

        val token = animationToken
        val cycleMs = KEY_LIT_MS + KEY_GAP_MS

        displayedSequence.indices.forEach { index ->
            val litAt = index * cycleMs
            val offAt = litAt + KEY_LIT_MS
            animationHandler.postDelayed({
                if (token != animationToken) return@postDelayed
                activeIndex = index
                showCompleted = false
                showTrail = false
                invalidate()
            }, litAt)
            animationHandler.postDelayed({
                if (token != animationToken) return@postDelayed
                activeIndex = -1
                invalidate()
            }, offAt)
        }

        val finalAt = displayedSequence.size * cycleMs - KEY_GAP_MS + FINAL_PAUSE_MS
        animationHandler.postDelayed({
            if (token != animationToken) return@postDelayed
            activeIndex = -1
            showCompleted = true
            showTrail = displayedSequence.size > 1
            invalidate()
            onAnimationFinished?.invoke(displayedSequence.joinToString(""))
        }, finalAt)
    }

    fun setSequence(sequence: String) {
        animationToken += 1
        animationHandler.removeCallbacksAndMessages(null)
        displayedSequence = sequence.lowercase().filter { it in COLEMAK_KEYS }.toList()
        activeIndex = if (displayedSequence.isEmpty()) -1 else displayedSequence.lastIndex
        showCompleted = displayedSequence.isNotEmpty()
        showTrail = displayedSequence.size > 1
        invalidate()
    }

    fun clearAnimationState() {
        animationToken += 1
        animationHandler.removeCallbacksAndMessages(null)
        displayedSequence = emptyList()
        activeIndex = -1
        showCompleted = false
        showTrail = false
        invalidate()
    }

    fun setOnAnimationFinishedListener(listener: (String) -> Unit) {
        onAnimationFinished = listener
    }

    fun applyTheme(theme: KeyboardThemePalette) {
        keyPaint.color = theme.keyBackground
        activePaint.color = theme.accentColor
        strokePaint.color = theme.strokeColor
        textPaint.color = theme.textPrimary
        activeTextPaint.color = Color.parseColor("#224FA3")
        arrowPaint.color = theme.previewArrow
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buildKeyboardLayout()

        val fontSize = min(width / 18f, height / 6.5f)
        textPaint.textSize = fontSize
        activeTextPaint.textSize = fontSize

        keyRects.forEach { (letter, rect) ->
            val isActive = when {
                showCompleted -> displayedSequence.contains(letter)
                activeIndex in displayedSequence.indices -> displayedSequence[activeIndex] == letter
                else -> false
            }
            canvas.drawRoundRect(rect, 16f, 16f, if (isActive) activePaint else keyPaint)
            canvas.drawRoundRect(rect, 16f, 16f, strokePaint)
            val paint = if (isActive) activeTextPaint else textPaint
            val textY = rect.centerY() - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(displayLabel(letter), rect.centerX(), textY, paint)
        }

        if (showTrail && displayedSequence.size > 1) {
            val points = displayedSequence.mapNotNull { keyRects[it]?.let { rect -> rect.centerX() to rect.centerY() } }
            if (points.size > 1) {
                drawArrowTrail(canvas, points)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Arrow trail drawing: straight lines for continuing direction,
    // quadratic-bezier arcs for direction reversals so overlapping segments
    // are clearly distinguishable (e.g. home-row left→right→left shortcuts).
    // ---------------------------------------------------------------------------

    private data class Segment(
        val from: Pair<Float, Float>,
        val to: Pair<Float, Float>,
        val control: Pair<Float, Float>?, // null = straight line
    )

    private fun drawArrowTrail(canvas: Canvas, points: List<Pair<Float, Float>>) {
        if (points.size < 2) return

        // Build segment list, detecting horizontal reversals
        // arcSign alternates -1/+1 so consecutive arcs bow in opposite directions
        // -1 → control point above the midpoint (arc bows up in screen space)
        // +1 → control point below the midpoint (arc bows down)
        var arcSign = -1
        val segments = mutableListOf<Segment>()

        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val currDx = to.first - from.first

            val isReversal = if (i > 0) {
                val prevDx = points[i].first - points[i - 1].first
                // Direction changed AND both segments have meaningful horizontal extent
                currDx * prevDx < 0 && abs(currDx) > 2f && abs(prevDx) > 2f
            } else false

            if (isReversal) {
                val midX = (from.first + to.first) / 2f
                val midY = (from.second + to.second) / 2f
                // Arc height proportional to horizontal span so longer spans bow more
                val arcHeight = abs(to.first - from.first) * 0.50f
                val controlY = midY + arcHeight * arcSign
                segments.add(Segment(from, to, Pair(midX, controlY)))
                arcSign *= -1
            } else {
                segments.add(Segment(from, to, null))
            }
        }

        // Draw all segments
        for (seg in segments) {
            val path = Path()
            path.moveTo(seg.from.first, seg.from.second)
            if (seg.control != null) {
                path.quadTo(seg.control.first, seg.control.second, seg.to.first, seg.to.second)
            } else {
                path.lineTo(seg.to.first, seg.to.second)
            }
            canvas.drawPath(path, arrowPaint)
        }

        // Arrow head at the last segment endpoint, tangent to the path
        val lastSeg = segments.last()
        if (lastSeg.control != null) {
            // Tangent of quadratic bezier at t=1: direction = endpoint - controlPoint
            drawArrowHead(canvas, lastSeg.control, lastSeg.to, 18f)
        } else {
            drawArrowHead(canvas, lastSeg.from, lastSeg.to, 18f)
        }
    }

    private fun buildKeyboardLayout() {
        if (keyRects.isNotEmpty()) return

        val rows = listOf("qwfpgjluy;", "arstdhneio'", "zxcvbkm,./")
        val horizontalPadding = 8f
        val verticalPadding = 8f
        val rowHeight = height / 3f
        rows.forEachIndexed { rowIndex, row ->
            val top = rowIndex * rowHeight + verticalPadding
            val availableWidth = width - horizontalPadding * 2
            val keyWidth = availableWidth / row.length
            row.forEachIndexed { index, char ->
                val left = horizontalPadding + index * keyWidth
                val rect = RectF(left, top, left + keyWidth - 6f, top + rowHeight - 14f)
                keyRects[char] = rect
            }
        }
    }

    private fun displayLabel(char: Char): String {
        return if (char.isLetter()) {
            char.uppercaseChar().toString()
        } else {
            char.toString()
        }
    }

    private fun drawArrowHead(canvas: Canvas, from: Pair<Float, Float>, to: Pair<Float, Float>, size: Float) {
        val angle = kotlin.math.atan2(to.second - from.second, to.first - from.first)
        val leftAngle = angle - Math.toRadians(150.0).toFloat()
        val rightAngle = angle + Math.toRadians(150.0).toFloat()

        val path = Path().apply {
            moveTo(to.first, to.second)
            lineTo(
                to.first + size * kotlin.math.cos(leftAngle),
                to.second + size * kotlin.math.sin(leftAngle),
            )
            moveTo(to.first, to.second)
            lineTo(
                to.first + size * kotlin.math.cos(rightAngle),
                to.second + size * kotlin.math.sin(rightAngle),
            )
        }
        canvas.drawPath(path, arrowPaint)
    }

    companion object {
        private const val COLEMAK_KEYS = "qwfpgjluy;arstdhneio'zxcvbkm,./"
        private const val KEY_LIT_MS = 700L
        private const val KEY_GAP_MS = 150L
        private const val FINAL_PAUSE_MS = 400L
    }
}
