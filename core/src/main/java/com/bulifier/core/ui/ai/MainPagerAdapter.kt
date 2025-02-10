package com.bulifier.core.ui.ai

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class BottomSheetPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AgentTabFragment()
            1 -> FilesFragment()
            else -> throw IllegalArgumentException("Invalid tab position")
        }
    }
}
