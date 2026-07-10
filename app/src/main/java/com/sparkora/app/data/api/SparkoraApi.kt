package com.sparkora.app.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SparkoraApi {

    // Auth
    @POST("api/auth/pin-login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ResponseBody>

    // Schedules (employee tokens are auto-scoped to their own jobs server-side)
    @GET("api/schedules")
    suspend fun schedules(
        @Query("from") from: String,
        @Query("to") to: String,
    ): List<ScheduleDto>

    @PATCH("api/schedules/{id}/complete")
    suspend fun completeSchedule(
        @Path("id") id: String,
        @Body body: ScheduleCompleteRequest,
    ): Response<ResponseBody>

    // Clock entries
    @GET("api/clock-entries")
    suspend fun clockEntries(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<ClockEntryDto>

    @POST("api/clock-entries")
    suspend fun createClockEntry(@Body body: ClockEntryCreateRequest): ClockEntryDto

    @PUT("api/clock-entries/{id}")
    suspend fun updateClockEntry(
        @Path("id") id: String,
        @Body body: ClockEntryUpdateRequest,
    ): ClockEntryDto

    @POST("api/geo/validate-clock")
    suspend fun validateClock(@Body body: GeoValidateRequest): GeoValidateResponse

    // Time off
    @GET("api/time-off-requests")
    suspend fun timeOffRequests(): List<TimeOffDto>

    @POST("api/time-off-requests")
    suspend fun createTimeOff(@Body body: TimeOffCreateRequest): TimeOffDto

    @DELETE("api/time-off-requests/{id}")
    suspend fun deleteTimeOff(@Path("id") id: String): Response<ResponseBody>

    // Payslips
    @GET("api/my/payslips")
    suspend fun myPayslips(): List<PayslipDto>

    // Profile
    @GET("api/employees/{id}")
    suspend fun employee(@Path("id") id: String): EmployeeDto
}
