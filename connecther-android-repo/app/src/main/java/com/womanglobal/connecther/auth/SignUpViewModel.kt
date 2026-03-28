package com.womanglobal.connecther.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.womanglobal.connecther.repository.AuthRepository
import com.womanglobal.connecther.repository.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val supabaseRepository = SupabaseRepository()

    fun signUp(email: String, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val mapped = authRepository.signUpWithEmail(email.trim(), password).fold(
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
