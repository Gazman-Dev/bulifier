package com.bulifier.core.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.bulifier.core.R
import com.bulifier.core.databinding.ActivityMainBinding
import com.bulifier.core.navigation.NavigationController
import com.bulifier.core.navigation.NavigationManager
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.ui.main.MainFragment
import com.bulifier.core.ui.main.ProjectsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

@AndroidEntryPoint
abstract class CoreActivity : AppCompatActivity(), NavigationController {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    override val navController by lazy {
        NavigationManager(this, R.id.regularFragments, R.id.cachedFragments)
    }

    protected fun setupUi(savedInstanceState: Bundle?) {
        setContentView(binding.root)
        setupPadding()

        if (savedInstanceState == null) {
            onLoadMainScreen()
        }
    }

    private fun setupPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }

    open fun onLoadMainScreen() = navigateToMain()
}


fun Fragment.navigateToMain() {
    navigateToMain(findNavController())
}

fun AppCompatActivity.navigateToMain() {
    navigateToMain(findNavController())
}

private fun navigateToMain(
    navController: NavigationController
) {
    when {
        AppSettings.project.value.projectName.isNotBlank() -> {
            navController.navigate(
                MainFragment::class.java,
                clearBackStack = true
            )
        }

        else -> {
            navController.navigate(ProjectsFragment::class.java, clearBackStack = true)
        }
    }
}