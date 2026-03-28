package com.womanglobal.connecther

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.womanglobal.connecther.auth.AuthGateActivity
import com.womanglobal.connecther.ui.theme.ConnectHerTheme
import kotlinx.coroutines.launch

private const val PREFS_ONBOARDING = "connecther_auth"
private const val KEY_INTRO_SEEN = "onboarding_intro_seen"

/**
 * Intro onboarding for first-time users before sign-in.
 * Swipeable screens with custom illustrations: Welcome, About, Services, Get Started.
 * Skip button, progress dots, Next/Get Started at bottom.
 * Gives Clerk time to load in the background for smooth sign-in.
 */
class OnboardingIntroActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_ONBOARDING, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INTRO_SEEN, false)) {
            startActivity(Intent(this, AuthGateActivity::class.java))
            finish()
            return
        }

        setContent {
            ConnectHerTheme(dynamicColor = false) {
                OnboardingIntroScreen(
                onComplete = {
                    // Don't set KEY_INTRO_SEEN here – AuthGateActivity sets it when shown.
                    // If AuthGateActivity crashes, user will see onboarding again on next launch.
                    startActivity(Intent(this, AuthGateActivity::class.java))
                    finish()
                }
            )
            }
        }
    }

    companion object {
        fun markIntroSeen(context: Context) {
            context.getSharedPreferences(PREFS_ONBOARDING, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_INTRO_SEEN, true)
                .apply()
        }

        fun hasSeenIntro(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_ONBOARDING, Context.MODE_PRIVATE)
                .getBoolean(KEY_INTRO_SEEN, false)
        }
    }
}

private val APP_BG = Color(0xFFF5EEEC)
private val PRIMARY = Color(0xFFC2185B)
private val ON_BG = Color(0xFF1A1A1A)
private val ON_SURFACE_VARIANT = Color(0xFF5C5C5C)

private data class ServiceItem(val name: String, val description: String)

private data class OnboardingPage(
    val title: String,
    val body: String,
    val imageResId: Int,
    val services: List<ServiceItem>? = null,
    val isLast: Boolean = false,
)

private val PAGES = listOf(
    OnboardingPage(
        title = "Welcome to ConnectHer",
        body = "Your trusted marketplace for connecting with skilled service providers. Find reliable help for your home and family.",
        imageResId = R.drawable.onboarding_welcome
    ),
    OnboardingPage(
        title = "How It Works",
        body = "Browse services, connect with vetted providers, and book directly through the app. Simple, secure, and built for you.",
        imageResId = R.drawable.onboarding_how_it_works
    ),
    OnboardingPage(
        title = "Our Services",
        body = "Connect with trusted providers for everyday needs:",
        imageResId = R.drawable.onboarding_services,
        services = listOf(
            ServiceItem("Mama Fua", "Professional laundry, ironing, and fabric care"),
            ServiceItem("Care Giver", "Elderly and child care support when you need it"),
            ServiceItem("House Manager", "Household management and supervision"),
            ServiceItem("Errand Girl", "Run errands and general assistance")
        )
    ),
    OnboardingPage(
        title = "Ready to Get Started?",
        body = "Create your account and discover trusted service providers in your area. Join ConnectHer today.",
        imageResId = R.drawable.onboarding_get_started,
        isLast = true
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingIntroScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(APP_BG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onComplete) {
                    Text("Skip", color = ON_SURFACE_VARIANT)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                OnboardingPageContent(PAGES[page])
            }

            // Progress dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(PAGES.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) PRIMARY
                                else Color(0xFFD0C9C6)
                            )
                    )
                }
            }

            // Next / Get Started button
            Button(
                onClick = {
                    if (pagerState.currentPage == PAGES.size - 1) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PRIMARY)
            ) {
                Text(
                    if (pagerState.currentPage == PAGES.size - 1) "Get Started"
                    else "Next"
                )
                Spacer(modifier = Modifier.size(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom illustration for each screen
        Image(
            painter = painterResource(page.imageResId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PRIMARY,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = ON_SURFACE_VARIANT,
            textAlign = TextAlign.Center
        )
        page.services?.let { services ->
            Spacer(modifier = Modifier.height(20.dp))
            services.forEach { service ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PRIMARY
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ON_SURFACE_VARIANT
                    )
                }
            }
        }
    }
}
