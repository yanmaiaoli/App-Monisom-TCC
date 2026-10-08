package com.example.monisom.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Preencha e-mail e senha.")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            runCatching {
                supabase.auth.signInWith(Email) {
                    email = current.email.trim()
                    password = current.password
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = friendlyError(e))
            }.onSuccess {
                _state.value = _state.value.copy(isLoading = false, error = null)
            }
        }
    }
}
