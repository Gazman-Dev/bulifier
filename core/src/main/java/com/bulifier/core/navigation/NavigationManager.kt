package com.bulifier.core.navigation

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.savedstate.SavedStateRegistry
import com.bulifier.core.BuildConfig
import com.bulifier.core.utils.Logger

// A data class to represent an entry in our custom back stack.
data class NavigationEntry(
    val fragmentTag: String,
    val isCached: Boolean
)

class NavigationManager(
    private val activity: AppCompatActivity,
    private val mainContainerId: Int,
    private val cachedContainerId: Int
) {
    private val logger = if (BuildConfig.NAVIGATION_LOGS) {
        Logger("NavigationManager")
    } else null
    private val fragmentManager = activity.supportFragmentManager

    // Custom back stack to keep track of which container each fragment was added to.
    private val navStack = mutableListOf<NavigationEntry>()

    companion object {
        private const val NAV_STACK_KEY = "NavigationManager.navStack"
    }

    init {
        // Restore the navStack using SavedStateRegistry.
        val restoredBundle = activity.savedStateRegistry.consumeRestoredStateForKey(NAV_STACK_KEY)
        restoredBundle?.getStringArrayList(NAV_STACK_KEY)?.forEach { entryStr ->
            val parts = entryStr.split(":")
            if (parts.size == 2) {
                navStack.add(NavigationEntry(parts[0], parts[1].toBoolean()))
            }
        }
        logger?.d("Restored navStack: ${navStack.joinToString()}")

        // If the navStack is empty but FragmentManager has restored fragments,
        // reconstruct the navStack from the fragments in our containers.
        if (navStack.isEmpty()) {
            // Get fragments in the main and cached containers.
            val mainFragments =
                fragmentManager.fragments.filter { it.id == mainContainerId && it.tag != null }
            val cachedFragments =
                fragmentManager.fragments.filter { it.id == cachedContainerId && it.tag != null }
            // Assume the order in fragmentManager.fragments reflects the navigation order.
            mainFragments.forEach { frag ->
                navStack.add(NavigationEntry(frag.tag!!, false))
            }
            cachedFragments.forEach { frag ->
                navStack.add(NavigationEntry(frag.tag!!, true))
            }
            logger?.d("Reconstructed navStack from FragmentManager: ${navStack.joinToString()}")
        }

        // Register a SavedStateProvider to save the navStack.
        activity.savedStateRegistry.registerSavedStateProvider(
            NAV_STACK_KEY,
            SavedStateRegistry.SavedStateProvider {
                val bundle = Bundle()
                val stackList = ArrayList<String>()
                navStack.forEach { entry ->
                    stackList.add("${entry.fragmentTag}:${entry.isCached}")
                }
                bundle.putStringArrayList(NAV_STACK_KEY, stackList)
                bundle
            })

        activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                popBackStack()
            }
        })
    }

    /**
     * Navigates to the specified fragment.
     *
     * @param fragmentClass The target fragment class.
     * @param args Optional arguments.
     * @param clearBackStack If true, all previous fragments are removed.
     * @param cached If true, the fragment is added to the cached container.
     *                           Otherwise, it's added to the main container.
     */
    fun navigate(
        fragmentClass: Class<out Fragment>,
        args: Bundle?,
        clearBackStack: Boolean = false,
        cached: Boolean = false
    ) {
        // If clearBackStack is requested, remove all fragments in our custom stack.
        if (clearBackStack) {
            logger?.d("Clearing back stack")
            val clearTransaction = fragmentManager.beginTransaction()
            navStack.forEach { entry ->
                fragmentManager.findFragmentByTag(entry.fragmentTag)?.let { frag ->
                    logger?.d("Removing fragment with tag ${entry.fragmentTag}")
                    clearTransaction.remove(frag)
                }
            }
            clearTransaction.commit()
            navStack.clear()
        }

        val tag = fragmentClass.name
        val transaction = fragmentManager.beginTransaction()

        if (cached) {
            logger?.d("Navigating to (cached) $tag")
            // Hide the main container and show the cached container.
            activity.findViewById<View>(mainContainerId)?.visibility = View.GONE
            activity.findViewById<View>(cachedContainerId)?.visibility = View.VISIBLE

            // Check if the fragment already exists in the cached container.
            var fragment = fragmentManager.findFragmentByTag(tag)
            if (fragment == null) {
                fragment = fragmentClass.getDeclaredConstructor().newInstance().apply {
                    if (args != null) arguments = args
                }
                transaction.add(cachedContainerId, fragment, tag)
                logger?.d("Added new cached fragment: $tag")
            } else {
                transaction.show(fragment)
                logger?.d("Reusing cached fragment: $tag")
            }
            transaction.commit()
            navStack.add(NavigationEntry(tag, isCached = true))
        } else {
            logger?.d("Navigating to (regular) $tag")
            // Hide the cached container and show the main container.
            activity.findViewById<View>(cachedContainerId)?.visibility = View.GONE
            activity.findViewById<View>(mainContainerId)?.visibility = View.VISIBLE

            // Hide any visible fragments in the main container.
            fragmentManager.fragments
                .filter { it.id == mainContainerId && !it.isHidden }
                .forEach { transaction.hide(it) }

            // Add the new regular fragment.
            val fragment = fragmentClass.getDeclaredConstructor().newInstance().apply {
                if (args != null) arguments = args
            }
            transaction.add(mainContainerId, fragment, tag)
            transaction.commit()
            navStack.add(NavigationEntry(tag, isCached = false))
        }
        logNavStack("After navigate to $tag")
    }

    /**
     * Pops the top fragment from the custom back stack and shows the previous fragment,
     * toggling container visibilities based on whether the fragment is cached.
     */
    fun popBackStack() {
        if (navStack.size <= 1) {
            logger?.d("Back stack empty or only one item, finishing activity")
            activity.finish()
            return
        }

        logNavStack("Before popBackStack")
        // Remove the top entry.
        val currentEntry = navStack.removeAt(navStack.lastIndex)
        val transaction = fragmentManager.beginTransaction()
        val currentFragment = fragmentManager.findFragmentByTag(currentEntry.fragmentTag)

        // For cached fragments, simply hide them; for regular, remove them.
        if (currentEntry.isCached) {
            currentFragment?.let {
                transaction.hide(it)
                logger?.d("Hiding cached fragment: ${currentEntry.fragmentTag}")
            }
        } else {
            currentFragment?.let {
                transaction.remove(it)
                logger?.d("Removing regular fragment: ${currentEntry.fragmentTag}")
            }
        }
        transaction.commit()

        // Peek at the new top entry.
        val topEntry = navStack.last()
        if (topEntry.isCached) {
            logger?.d("Top of back stack is cached: ${topEntry.fragmentTag}")
            activity.findViewById<View>(mainContainerId)?.visibility = View.GONE
            activity.findViewById<View>(cachedContainerId)?.visibility = View.VISIBLE

            // Ensure the top cached fragment is visible.
            fragmentManager.findFragmentByTag(topEntry.fragmentTag)?.let { frag ->
                fragmentManager.beginTransaction().show(frag).commit()
                logger?.d("Showing cached fragment: ${topEntry.fragmentTag}")
            }
        } else {
            logger?.d("Top of back stack is regular: ${topEntry.fragmentTag}")
            activity.findViewById<View>(cachedContainerId)?.visibility = View.GONE
            activity.findViewById<View>(mainContainerId)?.visibility = View.VISIBLE

            // Ensure the top regular fragment is visible.
            fragmentManager.findFragmentByTag(topEntry.fragmentTag)?.let { frag ->
                fragmentManager.beginTransaction().show(frag).commit()
                logger?.d("Showing regular fragment: ${topEntry.fragmentTag}")
            }
        }
        logNavStack("After popBackStack")
    }

    /**
     * Returns true if the custom back stack has less than 2 navigation entries.
     */
    fun isBackStackEmpty(): Boolean {
        val result = navStack.size < 2
        logger?.d("isBackStackEmpty? navStack size=${navStack.size} returns $result")
        return result
    }

    private fun logNavStack(context: String) {
        if (logger == null) {
            return
        }
        // Force any pending transactions to execute.
        fragmentManager.executePendingTransactions()

        val navStackString = navStack.joinToString(separator = " -> ") {
            "${it.fragmentTag} (${if (it.isCached) "cached" else "regular"})"
        }
        logger.d("$context - Custom navStack: $navStackString")

        // Get fragments in each container.
        val mainFragments = fragmentManager.fragments.filter { it.id == mainContainerId }
        val cachedFragments = fragmentManager.fragments.filter { it.id == cachedContainerId }
        val mainFragmentsString =
            mainFragments.joinToString(separator = ", ") { it.tag ?: "unknown" }
        val cachedFragmentsString =
            cachedFragments.joinToString(separator = ", ") { it.tag ?: "unknown" }
        logger.d("Main container fragments: $mainFragmentsString")
        logger.d("Cached container fragments: $cachedFragmentsString")

        // For each navStack entry, warn if it's missing from FragmentManager.
        val allFragmentTags = (mainFragments + cachedFragments).mapNotNull { it.tag }
        navStack.forEach { entry ->
            if (!allFragmentTags.contains(entry.fragmentTag)) {
                logger.w("NavStack entry ${entry.fragmentTag} is not present in FragmentManager!")
            }
        }

        // Check for fragments in FragmentManager that are not in navStack.
        // For main container fragments, always warn.
        mainFragments.forEach { frag ->
            if (navStack.none { it.fragmentTag == frag.tag }) {
                logger.w("Main container has tag ${frag.tag} but it's not in navStack!")
            }
        }
        // For cached container fragments, warn only if they're visible.
        cachedFragments.forEach { frag ->
            if (navStack.none { it.fragmentTag == frag.tag } && !frag.isHidden) {
                logger.w("Cached container has tag ${frag.tag} (visible) but it's not in navStack!")
            }
        }
    }

}


interface NavigationController {

    val navController: NavigationManager

    fun popBackStack() = navController.popBackStack()
    fun navigate(
        fragmentClass: Class<out Fragment>,
        args: Bundle? = null,
        clearBackStack: Boolean = false,
        cached: Boolean = false
    ) = navController.navigate(fragmentClass, args, clearBackStack, cached)

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