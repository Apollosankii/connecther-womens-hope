package com.womanglobal.connecther.utils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnLayout

/**
 * Expanding “shockwave” rings: scale up while alpha fades out, repeating.
 * Pivots are aligned to the center of [scaleOrigin] (e.g. the SOS button) so waves stay centered on it.
 */
object SosShockwaveAnimator {
    private const val DURATION_MS = 1700L
    private const val SCALE_END = 1.52f
    private const val ALPHA_START = 0.58f
    private const val ALPHA_END = 0f

    fun start(outer: View, middle: View, scaleOrigin: View): Pair<ObjectAnimator, ObjectAnimator> {
        val outerAnim = waveAnimator(outer)
        val middleAnim = waveAnimator(middle).apply {
            startDelay = DURATION_MS / 2
        }

        val host = scaleOrigin.parent as? View ?: outer
        // Defer past the current frame. After returning from another Activity, `doOnLayout` alone
        // can register while a layout is "requested" and then never get a callback for this host,
        // so the animators are created but never started (rings stay frozen).
        host.post {
            host.doOnLayout {
                alignPivotToCenterOf(outer, scaleOrigin)
                alignPivotToCenterOf(middle, scaleOrigin)
                resetRingVisual(outer)
                resetRingVisual(middle)
                outerAnim.start()
                middleAnim.start()
            }
        }

        return outerAnim to middleAnim
    }

    fun resetRings(outer: View, middle: View) {
        resetRingVisual(outer)
        resetRingVisual(middle)
    }

    /**
     * Sets [ring]'s transform pivot to the center of [target], in [ring]'s coordinate space.
     * Requires both views to share the same parent and be laid out.
     */
    private fun alignPivotToCenterOf(ring: View, target: View) {
        if (ring.width <= 0 || ring.height <= 0 || target.width <= 0 || target.height <= 0) return
        val cx = target.left + target.width / 2f
        val cy = target.top + target.height / 2f
        ring.pivotX = cx - ring.left
        ring.pivotY = cy - ring.top
    }

    private fun resetRingVisual(v: View) {
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = ALPHA_START
    }

    private fun waveAnimator(ring: View): ObjectAnimator {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, SCALE_END)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, SCALE_END)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, ALPHA_START, ALPHA_END)
        return ObjectAnimator.ofPropertyValuesHolder(ring, scaleX, scaleY, alpha).apply {
            duration = DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator(1.15f)
        }
    }
}
