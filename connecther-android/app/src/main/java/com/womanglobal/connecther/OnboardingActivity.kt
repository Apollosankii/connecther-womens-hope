package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.auth.AuthGateActivity
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.CurrentUser
import kotlinx.coroutines.launch

/**
 * Onboarding: collect profile (name, phone, email, title) after Clerk sign-in.
 * Inserts users row into Supabase with clerk_user_id; saves locally.
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnboardingScreen(
                onComplete = ::completeOnboarding,
                onBack = ::abortOnboarding
            )
        }
    }

    private fun completeOnboarding(
        title: String,
        firstName: String,
        lastName: String,
        phone: String,
        email: String
    ) {
        val clerkUser = Clerk.activeUser ?: run {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val clerkUserId = clerkUser.id
        val userId = "u_${clerkUserId.takeLast(8)}"

        val user = User(
            id = userId,
            first_name = firstName,
            last_name = lastName,
            title = title.ifEmpty { null },
            user_name = clerkUserId,
            nat_id = null,
            dob = null,
            gender = null,
            occupation = null,
            pic = null,
            isIdVerified = null,
            isMobileVerified = null,
            isAvailable = true,
            details = null,
            phoneNumber = phone.ifBlank { null },
            country = null,
            county = null,
            area_name = null
        )

        val payload = SupabaseData.InsertUserPayload(
            user_id = userId,
            clerk_user_id = clerkUserId,
            first_name = firstName,
            last_name = lastName,
            title = title.ifEmpty { "Ms" },
            phone = phone.ifBlank { "not_provided" },
            email = email.ifEmpty { "noemail@placeholder.local" },
            password = "clerk_migrated"
        )

        lifecycleScope.launch {
            runCatching {
                SupabaseData.insertUser(payload)
            }.onSuccess {
                completeOnboardingSuccess(user, firstName, lastName, phone, email, clerkUserId)
            }.onFailure { e ->
                Log.e("Onboarding", "Supabase insert failed", e)
                // User may already exist (e.g. signed in on another device). Treat as success.
                if (SupabaseData.isUserAlreadyExistsError(e)) {
                    completeOnboardingSuccess(user, firstName, lastName, phone, email, clerkUserId)
                    return@launch
                }
                val msg = when {
                    e is IllegalStateException -> e.message ?: "Clerk session not available."
                    e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.message?.contains("connection", ignoreCase = true) == true ||
                        e.cause?.message?.contains("timeout", ignoreCase = true) == true ||
                        e.cause?.message?.contains("connection", ignoreCase = true) == true ->
                        "Connection issue. Please check your internet and try again."
                    else -> "Could not complete registration. Please check your connection and try again."
                }
                Toast.makeText(this@OnboardingActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completeOnboardingSuccess(
        user: User,
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        clerkUserId: String
    ) {
        CurrentUser.setUser(user)
        getSharedPreferences("user_session", MODE_PRIVATE).edit()
            .putString("user_full_name", "$firstName $lastName")
            .putString("user_phone", phone.ifBlank { "" })
            .putString("user_email", email)
            .putString("user_id", clerkUserId)
            .putBoolean("isLoggedIn", true)
            .apply()
        AuthGateActivity.saveClerkUserId(this, clerkUserId)
        AuthGateActivity.markOnboardingComplete(this)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    /**
     * Abort onboarding: sign out from Clerk and return to sign-in screen.
     * Called when user presses back.
     */
    private fun abortOnboarding() {
        lifecycleScope.launch {
            try {
                Clerk.auth.signOut()
            } catch (_: Exception) { /* ignore */ }
            getSharedPreferences("connecther_auth", MODE_PRIVATE).edit()
                .remove(AuthGateActivity.KEY_CLERK_USER_ID)
                .putBoolean(AuthGateActivity.KEY_ONBOARDING_COMPLETE, false)
                .apply()
            startActivity(Intent(this@OnboardingActivity, AuthGateActivity::class.java))
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun OnboardingScreen(
    onComplete: (title: String, firstName: String, lastName: String, phone: String, email: String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete your profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome! Tell us a bit about yourself.",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (e.g. Mrs, Mr, Miss)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { phone = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue without phone number")
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let { msg ->
                Text(msg, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    when {
                        firstName.isBlank() -> error = "First name is required"
                        lastName.isBlank() -> error = "Last name is required"
                        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> error = "Enter a valid email"
                        else -> {
                            error = null
                            onComplete(title, firstName.trim(), lastName.trim(), phone.trim(), email.trim())
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }
}
