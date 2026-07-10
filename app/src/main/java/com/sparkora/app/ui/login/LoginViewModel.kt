package com.sparkora.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.data.SessionManager
import com.sparkora.app.data.SessionStore
import com.sparkora.app.data.repo.ApiResult
import com.sparkora.app.data.repo.SparkoraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val companyId: String = "",
    val serverUrl: String = SessionManager.DEFAULT_BASE_URL,
    val showPassword: Boolean = false,
    val showAdvanced: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val repository: SparkoraRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui

    init {
        viewModelScope.launch {
            val saved = session.load()
            _ui.update {
                it.copy(
                    email = saved.email,
                    companyId = saved.companyId,
                    serverUrl = saved.baseUrl,
                )
            }
        }
    }

    fun onEmailChange(value: String) = _ui.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }
    fun onCompanyIdChange(value: String) = _ui.update { it.copy(companyId = value, error = null) }
    fun onServerUrlChange(value: String) = _ui.update { it.copy(serverUrl = value, error = null) }
    fun toggleShowPassword() = _ui.update { it.copy(showPassword = !it.showPassword) }
    fun toggleAdvanced() = _ui.update { it.copy(showAdvanced = !it.showAdvanced) }

    fun login() {
        val state = _ui.value
        if (state.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            session.setBaseUrl(state.serverUrl)
            val result = repository.login(
                email = state.email.trim(),
                password = state.password,
                companyId = state.companyId.trim(),
            )
            when (result) {
                is ApiResult.Ok -> Unit // session flow flips the root UI to the app
                is ApiResult.Err -> _ui.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
