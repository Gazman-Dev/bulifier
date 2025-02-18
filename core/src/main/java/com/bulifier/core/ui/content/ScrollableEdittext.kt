package com.bulifier.core.ui.content

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatEditText

@SuppressLint("ClickableViewAccessibility")
open class ScrollableEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var doubleTapListener: (() -> Unit)? = null
    private var currentTextSize = textSize // Store the current text size
    private var isWrapped = false
    private val scroller = OverScroller(context) // Handles the fling effect

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!scroller.isFinished) {
                scroller.forceFinished(true)
            }
        }
        // Delegate the event to the GestureDetector
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)

        super.onTouchEvent(event)
        return true
    }

    fun setOnDoubleTapListener(listener: () -> Unit) {
        doubleTapListener = listener
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation() // Ensures the fling continues until finished
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        val layout = layout ?: return super.scrollTo(x, y) // Ensure the layout is ready

        // Calculate the maximum width of the content
        val maxHorizontalScroll = (0 until layout.lineCount)
            .maxOfOrNull { layout.getLineWidth(it).toInt() } ?: 0
        val maxHorizontalScrollBound = maxOf(0, maxHorizontalScroll - width)

        // Calculate the vertical bounds as before
        val maxVerticalScroll = maxOf(0, layout.height - height)

        // Constrain the scrolling values
        val boundedX = x.coerceIn(0, maxHorizontalScrollBound)
        val boundedY = y.coerceIn(0, maxVerticalScroll)

        super.scrollTo(boundedX, boundedY)
    }


    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isWrapped) {
                scrollBy(
                    0, distanceY.toInt()
                )
            } else {
                scrollBy(
                    distanceX.toInt(), distanceY.toInt()
                )
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val flingMultiplier = 1f // Amplifies the fling velocity
            val adjustedVelocityX = (velocityX * flingMultiplier).toInt()
            val adjustedVelocityY = (velocityY * flingMultiplier).toInt()

            val maxHorizontalScroll = layout.width - width // Prevents overflinging horizontally
            val maxVerticalScroll = layout.height - height // Prevents overflinging vertically

            val startX = scrollX
            val startY = scrollY

            val maxScrollX = if (isWrapped) 0 else maxHorizontalScroll
            val maxScrollY = maxVerticalScroll

            scroller.fling(
                startX, startY,         // Start position
                -adjustedVelocityX,     // Amplified X velocity
                -adjustedVelocityY,     // Amplified Y velocity
                0, maxScrollX,          // Horizontal range
                0, maxScrollY           // Vertical range
            )
            postInvalidateOnAnimation()
            return true
        }


        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTapListener?.invoke()
            return true
        }
    }

    fun setIsWrapped(isWrapped: Boolean) {
        this.isWrapped = isWrapped
        setHorizontallyScrolling(!isWrapped)
        requestLayout()
        invalidate()
    }


    // Custom property to control editability
    var isEditable: Boolean = false
        set(value) {
            field = value
            isFocusable = value
            isFocusableInTouchMode = value
            isCursorVisible = value
            isLongClickable = value

            if (!value) {
                // Clear focus and hide the cursor when not editable
                clearFocus()
            }
        }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            currentTextSize *= scaleFactor
            currentTextSize = currentTextSize.coerceIn(
                12f,
                100f
            ) // Ensure text size stays within reasonable bounds
            setTextSize(TypedValue.COMPLEX_UNIT_PX, currentTextSize)
            return true
        }
    }
}
