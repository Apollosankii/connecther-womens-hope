package com.womanglobal.connecther.onboarding

import androidx.annotation.DrawableRes

data class OnboardingPage(
    @DrawableRes val imageRes: Int,
    val title: String,
    val body: String,
)

