package com.womanglobal.connecther.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.womanglobal.connecther.HomeFragment
import com.womanglobal.connecther.ProfileFragment
import com.womanglobal.connecther.ui.fragments.JobsFragment
import com.womanglobal.connecther.ui.fragments.MessagesFragment
import com.womanglobal.connecther.ui.fragments.ServicesFragment

/**
 * Tab order: 0 Home, 1 Services, 2 Messages, 3 Jobs, 4 Profile
 */
class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> ServicesFragment()
            2 -> MessagesFragment()
            3 -> JobsFragment()
            4 -> ProfileFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
