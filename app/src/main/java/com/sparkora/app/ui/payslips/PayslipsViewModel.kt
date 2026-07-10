package com.sparkora.app.ui.payslips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.AppContainer
import com.sparkora.app.data.api.PayslipDto
import com.sparkora.app.data.repo.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PayslipsUiState(
    val loading: Boolean = true,
    val payslips: List<PayslipDto> = emptyList(),
    val error: String? = null,
)

class PayslipsViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(PayslipsUiState())
    val ui: StateFlow<PayslipsUiState> = _ui

    init {
        viewModelScope.launch {
            when (val result = container.repository.myPayslips()) {
                is ApiResult.Ok -> _ui.update {
                    it.copy(loading = false, payslips = result.value)
                }
                is ApiResult.Err -> _ui.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
