package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import com.bulifier.core.ui.utils.dpToPx

@SuppressLint("ClickableViewAccessibility")
class CanvasLineNumberEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ScrollableEditText(context, attrs) {

    // Paint object for drawing line numbers.
    private val lineNumberPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        // Scale text size to 80% of the main text size.
        textSize = this@CanvasLineNumberEditText.textSize * 0.8f
    }

    private val lineNumberAreaWidth = dpToPx(40f, context)

    init {
        // Adjust left padding to reserve space for line numbers.
        // Other paddings remain unchanged.
        setPadding(lineNumberAreaWidth.toInt(), paddingTop, paddingRight, paddingBottom)
        // Ensure text is drawn from the top.
        gravity = Gravity.TOP
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: run {
            super.onDraw(canvas)
            return
        }

        val scrollX = scrollX   // Horizontal scroll offset.
        val scrollY = scrollY   // Vertical scroll offset.
        val viewHeight = height

        // Save canvas state.
        canvas.save()
        // Cancel out the horizontal scroll translation so that line numbers stay fixed.
        canvas.translate(scrollX.toFloat(), 0f)

        // Determine visible lines.
        val firstVisibleLine = layout.getLineForVertical(scrollY)
        val lastVisibleLine = layout.getLineForVertical(scrollY + viewHeight)

        for (i in firstVisibleLine..lastVisibleLine) {
            val baseline = layout.getLineBaseline(i)
            val lineNumber = (i + 1).toString()
            val textWidth = lineNumberPaint.measureText(lineNumber)
            // Center the line number horizontally in the reserved area.
            val x = (lineNumberAreaWidth - textWidth) / 2f
            canvas.drawText(lineNumber, x, baseline.toFloat(), lineNumberPaint)
        }
        // Restore canvas state.
        canvas.restore()

        // Draw the main text (with the original scroll offsets).
        super.onDraw(canvas)
    }

}
