package com.womanglobal.connecther

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import com.womanglobal.connecther.databinding.FragmentHomeBinding
import com.womanglobal.connecther.utils.ThemeHelper

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var heartbeatAnimator: AnimatorSet? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupThemeToggle()
        setupSosButton()
        setupActionCards()

        return binding.root
    }

    private fun setupSosButton() {
        val pulseAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse_sos)
        binding.sosPulseRing.startAnimation(pulseAnim)

        startHeartbeat()

        binding.sosButton.setOnClickListener {
            startActivity(Intent(requireContext(), PanicActivity::class.java))
        }
    }

    private fun startHeartbeat() {
        val target = binding.sosButton

        val beat1ScaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.08f)
        val beat1ScaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.08f)
        beat1ScaleX.duration = 100
        beat1ScaleY.duration = 100

        val relax1ScaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1.08f, 1f)
        val relax1ScaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1.08f, 1f)
        relax1ScaleX.duration = 100
        relax1ScaleY.duration = 100

        val beat2ScaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.14f)
        val beat2ScaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.14f)
        beat2ScaleX.duration = 120
        beat2ScaleY.duration = 120

        val relax2ScaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1.14f, 1f)
        val relax2ScaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1.14f, 1f)
        relax2ScaleX.duration = 200
        relax2ScaleY.duration = 200

        val pause = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 1f)
        pause.duration = 700

        val beat1 = AnimatorSet().apply { playTogether(beat1ScaleX, beat1ScaleY) }
        val relax1 = AnimatorSet().apply { playTogether(relax1ScaleX, relax1ScaleY) }
        val beat2 = AnimatorSet().apply { playTogether(beat2ScaleX, beat2ScaleY) }
        val relax2 = AnimatorSet().apply { playTogether(relax2ScaleX, relax2ScaleY) }

        heartbeatAnimator = AnimatorSet().apply {
            playSequentially(beat1, relax1, beat2, relax2, pause)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (_binding != null) {
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun setupActionCards() {
        binding.emergencyPanicCard.setOnClickListener {
            startActivity(Intent(requireContext(), PanicActivity::class.java))
        }
        binding.emergencyContactsCard.setOnClickListener {
            startActivity(Intent(requireContext(), EmergencyContactsActivity::class.java))
        }
    }

    private fun setupThemeToggle() {
        updateThemeToggleIcon()
        binding.themeToggleButton.setOnClickListener {
            ThemeHelper.setDarkMode(requireContext(), !ThemeHelper.isDarkMode(requireContext()))
            // Don't call recreate() - AppCompatDelegate.setDefaultNightMode already triggers activity recreation
        }
    }

    private fun updateThemeToggleIcon() {
        val isDark = ThemeHelper.isDarkMode(requireContext())
        binding.themeToggleButton.setImageResource(
            if (isDark) R.drawable.ic_sun_24 else R.drawable.ic_moon_24
        )
    }

    override fun onResume() {
        super.onResume()
        // Update icon to reflect current theme
        updateThemeToggleIcon()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        heartbeatAnimator?.cancel()
        heartbeatAnimator = null
        _binding = null
    }
}
