package com.sparkora.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.AppContainer
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.data.repo.ApiResult
import com.sparkora.app.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ScheduleUiState(
    val loading: Boolean = true,
    val weekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val jobsByDay: Map<LocalDate, List<ScheduleDto>> = emptyMap(),
    val error: String? = null,
)

class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(ScheduleUiState())
    val ui: StateFlow<ScheduleUiState> = _ui

    init {
        load(_ui.value.weekStart)
    }

    fun previousWeek() = load(_ui.value.weekStart.minusWeeks(1))
    fun nextWeek() = load(_ui.value.weekStart.plusWeeks(1))
    fun thisWeek() = load(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )

    private fun load(weekStart: LocalDate) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, weekStart = weekStart, error = null) }
            val from = weekStart.format(Dates.YMD)
            val to = weekStart.plusDays(6).format(Dates.YMD)
            when (val result = container.repository.schedules(from, to)) {
                is ApiResult.Ok -> {
                    val grouped = result.value
                        .filter { it.status != "cancelled" }
                        .groupBy { Dates.parseDay(it.date) }
                        .filterKeys { it != null }
                        .mapKeys { it.key!! }
                        .mapValues { (_, jobs) -> jobs.sortedBy { it.startTime ?: "" } }
                    _ui.update { it.copy(loading = false, jobsByDay = grouped) }
                }
                is ApiResult.Err -> _ui.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
