package com.example.id.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.id.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            try {
                val response = authRepository.login(username, password)
                if (response.isSuccessful && response.body() != null) {
                    authRepository.saveToken(response.body()!!.token)
                    _loginState.value = LoginUiState.Success
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown login error"
                    _loginState.value = LoginUiState.Error(errorBody)
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }
}
