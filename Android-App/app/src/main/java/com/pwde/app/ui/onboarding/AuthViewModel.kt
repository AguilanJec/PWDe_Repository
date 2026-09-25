package com.pwde.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.remote.AuthRepository
import com.pwde.app.data.remote.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val cloudAvailable: Boolean,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val completed: Boolean = false,
) {
    val emailValid: Boolean get() = EMAIL_REGEX.matches(email.trim())
    val passwordLongEnough: Boolean get() = password.length >= MIN_PASSWORD
    val passwordsMatch: Boolean get() = password == confirmPassword

    companion object {
        const val MIN_PASSWORD = 8
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Shared by Sign in, Create account and Reset password. Talks only to [AuthRepository]. */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState(cloudAvailable = authRepository.isCloudAvailable))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun signIn() {
        val s = _state.value
        when {
            !s.emailValid -> fail("Please enter a valid email address.")
            s.password.isEmpty() -> fail("Please enter your password.")
            else -> submit { authRepository.signIn(s.email, s.password) }
        }
    }

    fun createAccount() {
        val s = _state.value
        when {
            !s.emailValid -> fail("Please enter a valid email address.")
            !s.passwordLongEnough -> fail("Password needs at least ${AuthUiState.MIN_PASSWORD} characters.")
            !s.passwordsMatch -> fail("The two passwords don't match.")
            else -> submit { authRepository.createAccount(s.email, s.password) }
        }
    }

    fun sendReset() {
        val s = _state.value
        if (!s.emailValid) return fail("Please enter a valid email address.")
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = authRepository.sendPasswordReset(s.email)) {
                AuthResult.Success -> _state.update {
                    it.copy(busy = false, info = "Check your inbox — we sent a reset link to ${s.email.trim()}.")
                }
                is AuthResult.Error -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    private fun submit(call: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = call()) {
                AuthResult.Success -> _state.update { it.copy(busy = false, completed = true) }
                is AuthResult.Error -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }
}
