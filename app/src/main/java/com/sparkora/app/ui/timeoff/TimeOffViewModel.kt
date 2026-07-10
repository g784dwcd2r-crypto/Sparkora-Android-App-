package com.sparkora.app.ui.timeoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.data.api.TimeOffCreateRequest
import com.sparkora.app.data.api.TimeOffDto
import com.sparkora.app.data.repo.ApiResult
import com.sparkora.app.util.Dates
import com.sparkora.app.util.newEntityId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TimeOffUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val requests: List<TimeOffDto> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

class TimeOffViewModel(private val repository: SparkoraRepository) : ViewModel() {

    private val _ui = MutableStateFlow(TimeOffUiState())
    val ui: StateFlow<TimeOffUiState> = _ui

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            when (val result = repository.timeOffRequests()) {
                is ApiResult.Ok -> _ui.update {
                    it.copy(loading = false, requests = result.value)
                }
                is ApiResult.Err -> _ui.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun consumeNotice() = _ui.update { it.copy(notice = null) }
    fun consumeError() = _ui.update { it.copy(error = null) }

    fun submit(start: LocalDate, end: LocalDate, leaveType: String, reason: String) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val days = (ChronoUnit.DAYS.between(start, end) + 1).coerceAtLeast(1)
            val body = TimeOffCreateRequest(
                id = newEntityId(),
                startDate = start.format(Dates.YMD),
                endDate = end.format(Dates.YMD),
                requestedDays = days.toDouble(),
                reason = reason.trim(),
                leaveType = leaveType,
            )
            when (val result = repository.createTimeOff(body)) {
                is ApiResult.Ok -> {
                    _ui.update { it.copy(notice = "Leave request submitted for approval.") }
                    refresh()
                }
                is ApiResult.Err -> _ui.update { it.copy(error = result.message) }
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun cancel(request: TimeOffDto) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            when (val result = repository.cancelTimeOff(request.id)) {
                is ApiResult.Ok -> {
                    _ui.update { it.copy(notice = "Request cancelled.") }
                    refresh()
                }
                is ApiResult.Err -> _ui.update { it.copy(error = result.message) }
            }
            _ui.update { it.copy(busy = false) }
        }
    }
}
