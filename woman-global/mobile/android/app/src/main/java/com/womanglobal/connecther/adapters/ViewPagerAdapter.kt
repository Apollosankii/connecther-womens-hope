package com.womanglobal.connecther.ui.adapter

import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.womanglobal.connecther.HomeFragment
import com.womanglobal.connecther.ProfileFragment
import com.womanglobal.connecther.ui.fragments.JobsFragment
import com.womanglobal.connecther.ui.fragments.MessagesFragment
import com.womanglobal.connecther.ui.fragments.ServicesFragment

class ViewPagerAdapter(activity: FragmentActivity, private val sharedPreferences: SharedPreferences) :
    FragmentStateAdapter(activity) {

    private val isProvider: Boolean = sharedPreferences.getBoolean("isProvider", false)

    override fun getItemCount(): Int = if (isProvider) 5 else 5

    override fun createFragment(position: Int): Fragment {
        return when {
            position == 0 -> HomeFragment() // Home screen
            !isProvider && position == 1 -> MessagesFragment() // Regular users: Messages screen
            !isProvider && position == 2 -> JobsFragment() // Regular users: Jobs screen
            !isProvider && position == 3 -> ServicesFragment() // Regular users: Services marketplace
            !isProvider && position == 4 -> ProfileFragment() // Regular users: Profile screen
            isProvider && position == 1 -> MessagesFragment() // Providers: Messages screen
            isProvider && position == 2 -> JobsFragment() // Providers: Jobs screen
            isProvider && position == 3 -> ServicesFragment() // Providers: Services marketplace
            isProvider && position == 4 -> ProfileFragment() // Providers: Profile screen
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
