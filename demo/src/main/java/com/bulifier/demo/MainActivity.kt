package com.bulifier.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bulifier.core.api.AiService
import com.bulifier.core.navigation.NavigationController
import com.bulifier.core.navigation.NavigationManager
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.ui.main.MainFragment
import com.bulifier.core.ui.main.ProjectsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationController {
    override val navController = NavigationManager(this, R.id.nav_host_fragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, AiService::class.java))
        setContentView(R.layout.activity_main)

        if(savedInstanceState != null) {
            return
        }
        navController.apply {
            navigate(ProjectsFragment::class.java, clearBackStack = true)
        }
        if (Prefs.projectName.flow.value.isNotBlank()) {
            navigate(MainFragment::class.java, clearBackStack = true, cacheFragment = true)
        } else {
            navigate(ProjectsFragment::class.java, clearBackStack = true)
        }
    }
}