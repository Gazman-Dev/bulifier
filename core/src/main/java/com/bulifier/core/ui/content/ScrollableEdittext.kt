package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText

@SuppressLint("ClickableViewAccessibility")
class ScrollableEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true

        // Ensure text does not wrap and scrolling is enabled
        setHorizontallyScrolling(true)
        // Set maxLines to a high number to prevent text wrapping
        maxLines = Integer.MAX_VALUE

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (lastTouchX - event.x).toInt()
                    val dy = (lastTouchY - event.y).toInt()
                    scrollBy(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            super.onTouchEvent(event)
        }
    }

    // Custom property to control editability
    var isEditable: Boolean = false
        set(value) {
            field = value
            isFocusable = value
            isFocusableInTouchMode = value
            isCursorVisible = value
            isLongClickable = value
        }
}
