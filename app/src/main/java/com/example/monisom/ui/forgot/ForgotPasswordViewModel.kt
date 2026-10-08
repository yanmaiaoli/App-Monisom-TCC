package com.example.monisom.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
)

class ForgotPasswordViewModel : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun resetPassword() {
        val current = _state.value
        if (current.email.isBlank()) {
            _state.value = current.copy(error = "Informe seu e-mail.")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            runCatching {
                supabase.auth.resetPasswordForEmail(email = current.email.trim())
            }.onSuccess {
                _state.value = _state.value.copy(isLoading = false, sent = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(isLoading = false, error = friendlyError(e))
            }
        }
    }
}
