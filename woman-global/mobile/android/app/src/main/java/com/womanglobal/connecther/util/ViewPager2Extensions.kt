package com.womanglobal.connecther.util

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Makes horizontal page swipes require a longer drag, so vertical scrolling inside a page wins
 * more often. Uses ViewPager2's internal RecyclerView touch slop (reflection).
 */
fun ViewPager2.reduceDragSensitivity(multiplier: Int = 8) {
    if (multiplier < 2) return
    try {
        val rvField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        rvField.isAccessible = true
        val recyclerView = rvField.get(this) as RecyclerView
        val slopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        slopField.isAccessible = true
        val base = slopField.getInt(recyclerView)
        slopField.setInt(recyclerView, base * multiplier)
    } catch (e: Exception) {
        Log.w("ViewPager2", "reduceDragSensitivity not applied", e)
    }
}
