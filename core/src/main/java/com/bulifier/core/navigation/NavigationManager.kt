package com.bulifier.core.navigation

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class NavigationManager(
    private val activity: AppCompatActivity,
    private val fragmentContainerId: Int
) {
    private val fragmentCache = mutableMapOf<Class<out Fragment>, Fragment>()
    private val fragmentManager = activity.supportFragmentManager
    private var allowToFinish = true

    init {
        activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                popBackStack()
            }
        })
        fragmentManager.addOnBackStackChangedListener {
            if (fragmentManager.backStackEntryCount == 0 && allowToFinish) {
                activity.finish()
            }
        }
    }

    internal fun navigate(
        fragmentClass: Class<out Fragment>,
        args: Bundle?,
        clearBackStack: Boolean,
        cacheFragment: Boolean
    ) {
        val fragment: Fragment = if (cacheFragment && fragmentCache.containsKey(fragmentClass)) {
            val cachedFragment = fragmentCache[fragmentClass]!!
            args?.let {
                val cachedArgs = cachedFragment.arguments
                if (cachedArgs == null) {
                    cachedFragment.arguments = args
                } else {
                    cachedArgs.putAll(args)
                }
            }
            cachedFragment
        } else {
            val newFragment = fragmentClass.getDeclaredConstructor().newInstance().apply {
                if (args != null) {
                    arguments = args
                }
            }
            if (cacheFragment) {
                fragmentCache[fragmentClass] = newFragment
            }
            newFragment
        }

        if (clearBackStack) {
            allowToFinish = false
            fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        fragmentManager.beginTransaction()
            .replace(fragmentContainerId, fragment, fragmentClass.name)
            .addToBackStack(fragmentClass.name)
            .commit()
        allowToFinish = true
    }

    internal fun popBackStack() {
        fragmentManager.popBackStack()
    }

    internal fun isBackStackEmpty() = fragmentManager.backStackEntryCount <= 1
}

interface NavigationController {

    val navController: NavigationManager

    fun popBackStack() = navController.popBackStack()
    fun navigate(
        fragmentClass: Class<out Fragment>,
        args: Bundle? = null,
        clearBackStack: Boolean = false,
        cacheFragment: Boolean = false
    ) = navController.navigate(fragmentClass, args, clearBackStack, cacheFragment)

    fun isBackStackEmpty() = navController.isBackStackEmpty()
}

fun View.findNavController(): NavigationController {
    var context = this.context
    while (context is ContextWrapper) {
        if (context is NavigationController) {
            return context
        }
        context = context.baseContext
    }
    throw IllegalStateException("View is not attached to a NavigationController")
}

fun Fragment.findNavController(): NavigationController {
    val activity = this.activity
    if (activity is NavigationController) {
        return activity
    }
    throw IllegalStateException("Fragment is not attached to a NavigationController")
}

// Extension function for Activity
fun Activity.findNavController(): NavigationController {
    if (this is NavigationController) {
        return this
    }
    throw IllegalStateException("Activity is not a NavigationController")
}