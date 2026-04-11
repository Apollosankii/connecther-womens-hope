package com.womanglobal.connecther.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Wraps a vertical [androidx.recyclerview.widget.RecyclerView] that lives inside a horizontal
 * [androidx.viewpager2.widget.ViewPager2]. Stops the pager's inner RecyclerView from winning
 * touch contention so the list can scroll up/down reliably.
 *
 * The scrollable must be the only direct child.
 */
class VerticalScrollInViewPagerHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        relayDisallowToPagerAncestors(e)
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        relayDisallowToPagerAncestors(e)
        return super.onTouchEvent(e)
    }

    private fun relayDisallowToPagerAncestors(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - initialX
                val dy = e.y - initialY
                val adx = abs(dx)
                val ady = abs(dy)
                if (adx <= touchSlop && ady <= touchSlop) return

                if (ady > adx) {
                    parent.requestDisallowInterceptTouchEvent(true)
                } else {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
