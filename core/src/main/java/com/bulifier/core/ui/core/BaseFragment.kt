package com.bulifier.core.ui.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<T : ViewBinding> : Fragment() {

    protected lateinit var binding: T

    final override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = createBinding(inflater, container).run {
        binding = this
        root
    }

    protected abstract fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): T
}