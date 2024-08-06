package com.bulifier.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.bulifier.core.prefs.Prefs

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val navGraph =
            navController.navInflater.inflate(com.bulifier.core.R.navigation.core_nav_graph)

        if (Prefs.projectName.value != null) {
            navGraph.setStartDestination(com.bulifier.core.R.id.mainFragment)
        } else {
            navGraph.setStartDestination(com.bulifier.core.R.id.projectsFragment)
        }

        navController.graph = navGraph
    }
}