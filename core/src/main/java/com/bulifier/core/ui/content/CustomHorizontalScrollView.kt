package com.bulifier.core.ui.content

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class CustomHorizontalScrollView(context: Context, attrs: AttributeSet?) :
    HorizontalScrollView(context, attrs) {
    var onScrollChangedListener: ((Int, Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollChangedListener?.invoke(l, t)
    }
}
