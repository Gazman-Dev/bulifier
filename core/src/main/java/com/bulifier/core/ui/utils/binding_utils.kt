package com.bulifier.core.ui.utils

import android.view.View
import androidx.annotation.BoolRes
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter

@BindingAdapter("android:setVisible")
fun setVisible(view: View, @BoolRes visibleRes: Int) {
    view.isVisible = view.resources.getBoolean(visibleRes)
}