package com.womanglobal.connecther.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womanglobal.connecther.repository.AuthRepository
import com.womanglobal.connecther.repository.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val supabaseRepository = SupabaseRepository()

    fun signInWithEmail(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val mapped = authRepository.signInWithEmail(email.trim(), password).fold(
                onSuccess = { user ->
                    supabaseRepository.syncAuthenticatedFirebaseUser(user.uid, user.email)
                    Result.success(Unit)
                },
                onFailure = { Result.failure(it) },
            )
            withContext(Dispatchers.Main.immediate) { onResult(mapped) }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val mapped = authRepository.signInWithGoogleIdToken(idToken).fold(
                onSuccess = { user ->
                    supabaseRepository.syncAuthenticatedFirebaseUser(user.uid, user.email)
                    Result.success(Unit)
                },
                onFailure = { Result.failure(it) },
            )
            withContext(Dispatchers.Main.immediate) { onResult(mapped) }
        }
    }
}
