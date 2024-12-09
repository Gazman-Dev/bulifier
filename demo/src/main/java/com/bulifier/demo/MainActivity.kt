package com.bulifier.demo

import android.os.Bundle
import com.bulifier.core.ui.CoreActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : CoreActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi(savedInstanceState)
    }
}