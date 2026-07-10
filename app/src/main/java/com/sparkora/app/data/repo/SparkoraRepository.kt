package com.sparkora.app.data.repo

import com.sparkora.app.data.SessionManager
import com.sparkora.app.data.api.ApiErrorBody
import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.api.ClockEntryCreateRequest
import com.sparkora.app.data.api.ClockEntryDto
import com.sparkora.app.data.api.ClockEntryUpdateRequest
import com.sparkora.app.data.api.EmployeeDto
import com.sparkora.app.data.api.GeoValidateRequest
import com.sparkora.app.data.api.GeoValidateResponse
import com.sparkora.app.data.api.LoginRequest
import com.sparkora.app.data.api.PayslipDto
import com.sparkora.app.data.api.ScheduleCompleteRequest
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.data.api.TimeOffCreateRequest
import com.sparkora.app.data.api.TimeOffDto
import retrofit2.HttpException
import java.io.IOException

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Err(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

class SparkoraRepository(
    private val apiProvider: ApiProvider,
    private val session: SessionManager,
) {

    private fun parseErrorBody(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val body = apiProvider.json.decodeFromString<ApiErrorBody>(raw)
            body.message ?: body.error
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Wraps an API call into an [ApiResult]. On 401 for authenticated calls the
     * stored session is cleared, which sends the UI back to the login screen.
     */
    private suspend fun <T> safe(authed: Boolean = true, block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Ok(block())
        } catch (e: HttpException) {
            val message = parseErrorBody(e.response()?.errorBody()?.string())
                ?: "Server error (${e.code()})"
            if (authed && e.code() == 401) session.clear()
            ApiResult.Err(message, e.code())
        } catch (_: IOException) {
            ApiResult.Err("Cannot reach the server. Please check your connection.")
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Something went wrong. Please try again.")
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String, companyId: String?): ApiResult<Unit> {
        return safe(authed = false) {
            val response = apiProvider.api().login(
                LoginRequest(
                    email = email,
                    pin = password,
                    companyId = companyId?.takeIf { it.isNotBlank() },
                )
            )
            val body = response.body()
            if (response.isSuccessful && body?.success == true &&
                !body.token.isNullOrBlank() && !body.employeeId.isNullOrBlank()
            ) {
                session.saveLogin(
                    token = body.token,
                    employeeId = body.employeeId,
                    employeeName = body.name.orEmpty(),
                    email = email,
                    companyId = companyId.orEmpty(),
                )
            } else {
                val message = parseErrorBody(response.errorBody()?.string())
                    ?: body?.error
                    ?: when (response.code()) {
                        401 -> "Incorrect email or password."
                        402 -> "This company's subscription has expired."
                        429 -> "Too many attempts — please wait 15 minutes and try again."
                        else -> "Login failed (${response.code()})."
                    }
                throw IllegalStateException(message)
            }
        }
    }

    suspend fun logout() {
        try {
            apiProvider.api().logout()
        } catch (_: Exception) {
            // best-effort — local sign-out always proceeds
        }
        session.clear()
    }

    // ── Data ────────────────────────────────────────────────────────────────

    suspend fun schedules(from: String, to: String): ApiResult<List<ScheduleDto>> =
        safe { apiProvider.api().schedules(from, to) }

    suspend fun completeSchedule(id: String): ApiResult<Unit> =
        safe {
            val response = apiProvider.api().completeSchedule(id, ScheduleCompleteRequest())
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    parseErrorBody(response.errorBody()?.string())
                        ?: "Could not mark the job complete (${response.code()})."
                )
            }
        }

    suspend fun clockEntries(from: String? = null, to: String? = null): ApiResult<List<ClockEntryDto>> =
        safe { apiProvider.api().clockEntries(from, to) }

    suspend fun createClockEntry(body: ClockEntryCreateRequest): ApiResult<ClockEntryDto> =
        safe { apiProvider.api().createClockEntry(body) }

    suspend fun updateClockEntry(id: String, body: ClockEntryUpdateRequest): ApiResult<ClockEntryDto> =
        safe { apiProvider.api().updateClockEntry(id, body) }

    suspend fun validateClock(clientId: String, lat: Double, lng: Double): ApiResult<GeoValidateResponse> =
        safe { apiProvider.api().validateClock(GeoValidateRequest(clientId, lat, lng)) }

    suspend fun timeOffRequests(): ApiResult<List<TimeOffDto>> =
        safe { apiProvider.api().timeOffRequests() }

    suspend fun createTimeOff(body: TimeOffCreateRequest): ApiResult<TimeOffDto> =
        safe { apiProvider.api().createTimeOff(body) }

    suspend fun cancelTimeOff(id: String): ApiResult<Unit> =
        safe {
            val response = apiProvider.api().deleteTimeOff(id)
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    parseErrorBody(response.errorBody()?.string())
                        ?: "Could not cancel the request (${response.code()})."
                )
            }
        }

    suspend fun myPayslips(): ApiResult<List<PayslipDto>> =
        safe { apiProvider.api().myPayslips() }

    suspend fun myProfile(): ApiResult<EmployeeDto> =
        safe {
            val employeeId = session.load().employeeId
                ?: throw IllegalStateException("Not signed in.")
            apiProvider.api().employee(employeeId)
        }
}
