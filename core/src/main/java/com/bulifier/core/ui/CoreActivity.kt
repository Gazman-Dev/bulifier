package com.bulifier.core.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.R
import com.bulifier.core.api.AiService
import com.bulifier.core.databinding.ActivityMainBinding
import com.bulifier.core.navigation.NavigationController
import com.bulifier.core.navigation.NavigationManager
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.ui.main.MainFragment
import com.bulifier.core.ui.main.ProjectsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
abstract class CoreActivity : AppCompatActivity(), NavigationController {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    override val navController = NavigationManager(this, R.id.root)

    protected fun setupUi(savedInstanceState: Bundle?) {
        setContentView(binding.root)
        setupPadding()

        if (savedInstanceState == null) {
            onLoadMainScreen()
            lifecycleScope.launch {
                onServiceStarted()
            }
        }
    }

    private fun setupPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
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

    open suspend fun onServiceStarted() {
        delay(1)
        startService(Intent(this@CoreActivity, AiService::class.java))
    }
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
        Prefs.projectName.flow.value.isNotBlank() -> {
            navController.navigate(
                MainFragment::class.java,
                clearBackStack = true,
                cacheFragment = true
            )
        }

        else -> {
            navController.navigate(ProjectsFragment::class.java, clearBackStack = true)
        }
    }
}