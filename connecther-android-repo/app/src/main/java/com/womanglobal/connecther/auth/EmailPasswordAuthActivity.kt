package com.womanglobal.connecther.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.repository.AuthRepository
import com.womanglobal.connecther.ui.theme.ConnectHerTheme
import kotlinx.coroutines.launch

private val AppBg = Color(0xFFF5EEEC)
private val Primary = Color(0xFFC2185B)

/**
 * Email/password + Google sign-in. If a Firebase session already exists and the user did not
 * explicitly sign out, [AuthRouter] sends them to Home or Onboarding.
 */
class EmailPasswordAuthActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    this,
                    "Google sign-in did not return an ID token. Check default_web_client_id in strings.xml.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                return@registerForActivityResult
            }
            viewModel.signInWithGoogleIdToken(token) { res ->
                res.onSuccess {
                    lifecycleScope.launch {
                        AuthRouter.routeAfterSignIn(this@EmailPasswordAuthActivity)
                    }
                }
                res.onFailure {
                    android.widget.Toast.makeText(
                        this@EmailPasswordAuthActivity,
                        FirebaseAuthErrorMapper.message(it),
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } catch (e: ApiException) {
            if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) return@registerForActivityResult
            android.widget.Toast.makeText(
                this,
                FirebaseAuthErrorMapper.message(e),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            ConnectHerTheme(dynamicColor = false) {
                val activity = LocalContext.current as ComponentActivity
                LaunchedEffect(Unit) {
                    getSharedPreferences(AuthPreferences.PREFS_AUTH, MODE_PRIVATE).edit()
                        .putBoolean(AuthPreferences.KEY_INTRO_SEEN, true)
                        .apply()
                }
                LaunchedEffect(Unit) {
                    if (!AuthPreferences.isSignedOutRecently(activity) &&
                        FirebaseAuth.getInstance().currentUser != null
                    ) {
                        AuthRouter.routeAfterSignIn(activity)
                    }
                }
                LoginScreen(
                    onSignUp = {
                        startActivity(
                            Intent(this@EmailPasswordAuthActivity, SignUpActivity::class.java),
                        )
                    },
                    onGoogle = {
                        val client = AuthRepository().googleSignInClient(this@EmailPasswordAuthActivity)
                        googleLauncher.launch(client.signInIntent)
                    },
                    onSuccess = {
                        lifecycleScope.launch {
                            AuthRouter.routeAfterSignIn(this@EmailPasswordAuthActivity)
                        }
                    },
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    onSignUp: () -> Unit,
    onGoogle: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: LoginViewModel,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ConnectHer",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                error = when {
                    email.isBlank() -> "Enter your email."
                    password.isBlank() -> "Enter your password."
                    else -> null
                }
                if (error != null) return@Button
                loading = true
                viewModel.signInWithEmail(email, password) { res ->
                    loading = false
                    res.onSuccess { onSuccess() }
                    res.onFailure { error = FirebaseAuthErrorMapper.message(it) }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                error = null
                onGoogle()
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSignUp, enabled = !loading) {
            Text("Create an account")
        }
    }
}
