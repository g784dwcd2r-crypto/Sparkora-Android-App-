package com.sparkora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparkora.app.AppContainer
import com.sparkora.app.data.api.ClockEntryCreateRequest
import com.sparkora.app.data.api.ClockEntryDto
import com.sparkora.app.data.api.ClockEntryUpdateRequest
import com.sparkora.app.data.api.GeoValidateResponse
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.data.repo.ApiResult
import com.sparkora.app.util.Dates
import com.sparkora.app.util.newEntityId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

data class OverridePrompt(
    val job: ScheduleDto?,
    val geo: GeoValidateResponse,
) {
    val message: String
        get() {
            val distance = geo.distanceMetres?.roundToInt()?.let { "${it}m" } ?: "too far"
            val radius = geo.radiusMetres?.roundToInt()?.let { "${it}m" } ?: "the allowed radius"
            return "You appear to be $distance from the client's site (allowed: $radius). " +
                "Clock in anyway? Your manager will see this was overridden."
        }
}

data class HomeUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val todayJobs: List<ScheduleDto> = emptyList(),
    val activeEntry: ClockEntryDto? = null,
    val todayEntries: List<ClockEntryDto> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
    val overridePrompt: OverridePrompt? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            loadData()
            _ui.update { it.copy(loading = false) }
        }
    }

    private suspend fun loadData() {
        val today = Dates.todayString()
        val weekAgo = Dates.today().minusDays(7).format(Dates.YMD)

        when (val jobs = container.repository.schedules(today, today)) {
            is ApiResult.Ok -> _ui.update { state ->
                state.copy(todayJobs = jobs.value.filter { it.status != "cancelled" })
            }
            is ApiResult.Err -> _ui.update { it.copy(error = jobs.message) }
        }

        when (val entries = container.repository.clockEntries(from = weekAgo)) {
            is ApiResult.Ok -> _ui.update { state ->
                val all = entries.value
                state.copy(
                    activeEntry = all.firstOrNull { it.clockOut.isNullOrBlank() },
                    todayEntries = all.filter { entry ->
                        Dates.parseDay(entry.clockIn) == Dates.today()
                    },
                )
            }
            is ApiResult.Err -> _ui.update { it.copy(error = entries.message) }
        }
    }

    fun consumeNotice() = _ui.update { it.copy(notice = null) }
    fun consumeError() = _ui.update { it.copy(error = null) }
    fun dismissOverride() = _ui.update { it.copy(overridePrompt = null) }

    fun confirmOverride() {
        val prompt = _ui.value.overridePrompt ?: return
        _ui.update { it.copy(overridePrompt = null) }
        clockIn(prompt.job, force = true)
    }

    /**
     * Clock in, optionally against a scheduled job. GPS is captured when
     * permission is granted; the company's geofence rules are checked first
     * when the job has a client attached.
     */
    fun clockIn(job: ScheduleDto?, force: Boolean = false) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }

            val session = container.session.load()
            val employeeId = session.employeeId
            if (employeeId == null) {
                _ui.update { it.copy(busy = false, error = "Session expired — please sign in again.") }
                return@launch
            }

            val location = container.location.currentLocation()

            var geo: GeoValidateResponse? = null
            val clientId = job?.clientId
            if (clientId != null && location != null) {
                when (val result = container.repository.validateClock(
                    clientId, location.latitude, location.longitude,
                )) {
                    is ApiResult.Ok -> geo = result.value
                    is ApiResult.Err -> Unit // treat as unverified rather than blocking
                }
            }

            if (geo != null && !geo.allowed && !force) {
                if (geo.overrideAllowed == true) {
                    _ui.update { it.copy(busy = false, overridePrompt = OverridePrompt(job, geo)) }
                } else {
                    val distance = geo.distanceMetres?.roundToInt() ?: 0
                    val radius = geo.radiusMetres?.roundToInt() ?: 0
                    _ui.update {
                        it.copy(
                            busy = false,
                            error = "Too far from the client's site to clock in " +
                                "(${distance}m away, allowed ${radius}m).",
                        )
                    }
                }
                return@launch
            }

            val body = ClockEntryCreateRequest(
                id = newEntityId(),
                employeeId = employeeId,
                clientId = clientId,
                clockIn = Instant.now().toString(),
                clockInLat = location?.latitude,
                clockInLng = location?.longitude,
                geoVerified = geo?.geoVerified == true,
                geoDistanceM = geo?.distanceMetres,
                geoOverride = force,
            )

            when (val result = container.repository.createClockEntry(body)) {
                is ApiResult.Ok -> {
                    val site = job?.clientName?.let { " at $it" } ?: ""
                    _ui.update { it.copy(notice = "Clocked in$site — have a great shift!") }
                    loadData()
                }
                is ApiResult.Err -> _ui.update { it.copy(error = result.message) }
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun clockOut() {
        val active = _ui.value.activeEntry ?: return
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }

            val location = container.location.currentLocation()
            val body = ClockEntryUpdateRequest(
                employeeId = active.employeeId ?: "",
                clockIn = active.clockIn ?: Instant.now().toString(),
                clockOut = Instant.now().toString(),
                clockOutLat = location?.latitude,
                clockOutLng = location?.longitude,
            )

            when (val result = container.repository.updateClockEntry(active.id, body)) {
                is ApiResult.Ok -> {
                    val duration = Dates.durationSince(active.clockIn)
                        ?.let { Dates.formatDuration(it) }
                    _ui.update {
                        it.copy(notice = if (duration != null) "Clocked out — $duration worked." else "Clocked out.")
                    }
                    completeMatchingSchedule(active.clientId)
                    loadData()
                }
                is ApiResult.Err -> _ui.update { it.copy(error = result.message) }
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    /** Mirror of the web staff portal: closing a shift marks today's matching job complete. */
    private suspend fun completeMatchingSchedule(clientId: String?) {
        if (clientId == null) return
        val match = _ui.value.todayJobs.firstOrNull { job ->
            job.clientId == clientId && (job.status == "scheduled" || job.status == "in-progress")
        } ?: return
        container.repository.completeSchedule(match.id)
    }
}
