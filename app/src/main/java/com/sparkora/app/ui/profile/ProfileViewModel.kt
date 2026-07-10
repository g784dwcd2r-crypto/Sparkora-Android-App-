package com.sparkora.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.AppContainer
import com.sparkora.app.data.api.EmployeeDto
import com.sparkora.app.data.repo.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: EmployeeDto? = null,
    val error: String? = null,
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui

    init {
        viewModelScope.launch {
            when (val result = container.repository.myProfile()) {
                is ApiResult.Ok -> _ui.update {
                    it.copy(loading = false, profile = result.value)
                }
                is ApiResult.Err -> _ui.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.repository.logout()
            // SessionManager.clear() flips the root composable back to the login screen.
        }
    }
}
