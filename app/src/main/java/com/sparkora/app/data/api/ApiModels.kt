package com.sparkora.app.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The backend serves PostgreSQL NUMERIC columns as JSON strings ("12.50") and
 * DOUBLE PRECISION columns as JSON numbers, so numeric fields must accept both.
 */
object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content.toDoubleOrNull()
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

// ── Auth ────────────────────────────────────────────────────────────────────

@Serializable
data class LoginRequest(
    val role: String = "employee",
    val email: String,
    val pin: String,
    val companyId: String? = null,
)

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val role: String? = null,
    val token: String? = null,
    val employeeId: String? = null,
    val name: String? = null,
    val lang: String? = null,
    val theme: String? = null,
    val error: String? = null,
)

@Serializable
data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
)

// ── Schedules ───────────────────────────────────────────────────────────────

@Serializable
data class ScheduleDto(
    val id: String,
    val date: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("employee_id") val employeeId: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val status: String? = null,
    val notes: String? = null,
    val recurrence: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("employee_name") val employeeName: String? = null,
)

@Serializable
data class ScheduleCompleteRequest(
    val checklist: List<String> = emptyList(),
)

// ── Clock entries ───────────────────────────────────────────────────────────

@Serializable
data class ClockEntryDto(
    val id: String,
    @SerialName("employee_id") val employeeId: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("clock_in") val clockIn: String? = null,
    @SerialName("clock_out") val clockOut: String? = null,
    val notes: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("clock_in_lat") val clockInLat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("clock_in_lng") val clockInLng: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("clock_out_lat") val clockOutLat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("clock_out_lng") val clockOutLng: Double? = null,
    @SerialName("geo_verified") val geoVerified: Boolean? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("geo_distance_m") val geoDistanceM: Double? = null,
    @SerialName("geo_override") val geoOverride: Boolean? = null,
    @SerialName("validated_by_manager") val validatedByManager: Boolean? = null,
    @SerialName("rejected_by_manager") val rejectedByManager: Boolean? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
)

@Serializable
data class ClockEntryCreateRequest(
    val id: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("clock_in") val clockIn: String,
    @SerialName("clock_out") val clockOut: String? = null,
    val notes: String = "",
    @SerialName("clock_in_lat") val clockInLat: Double? = null,
    @SerialName("clock_in_lng") val clockInLng: Double? = null,
    @SerialName("geo_verified") val geoVerified: Boolean = false,
    @SerialName("geo_distance_m") val geoDistanceM: Double? = null,
    @SerialName("geo_override") val geoOverride: Boolean = false,
)

/**
 * PUT /api/clock-entries/:id — the backend's validation schema requires
 * employee_id and clock_in even on update, so we always echo the originals.
 */
@Serializable
data class ClockEntryUpdateRequest(
    @SerialName("employee_id") val employeeId: String,
    @SerialName("clock_in") val clockIn: String,
    @SerialName("clock_out") val clockOut: String? = null,
    val notes: String? = null,
    @SerialName("clock_out_lat") val clockOutLat: Double? = null,
    @SerialName("clock_out_lng") val clockOutLng: Double? = null,
)

// ── Geofence validation ─────────────────────────────────────────────────────

@Serializable
data class GeoValidateRequest(
    val clientId: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class GeoValidateResponse(
    val allowed: Boolean = true,
    val withinRadius: Boolean? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val distanceMetres: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val radiusMetres: Double? = null,
    val geoEnabled: Boolean? = null,
    val overrideAllowed: Boolean? = null,
    val geoVerified: Boolean = false,
    val reason: String? = null,
    val clientName: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val clientLat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val clientLng: Double? = null,
)

// ── Time off ────────────────────────────────────────────────────────────────

@Serializable
data class TimeOffDto(
    val id: String,
    @SerialName("employee_id") val employeeId: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("requested_days") val requestedDays: Double? = null,
    val reason: String? = null,
    @SerialName("leave_type") val leaveType: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TimeOffCreateRequest(
    val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("requested_days") val requestedDays: Double = 1.0,
    val reason: String = "",
    @SerialName("leave_type") val leaveType: String = "annual",
)

// ── Payslips ────────────────────────────────────────────────────────────────

@Serializable
data class PayslipDto(
    val id: String,
    @SerialName("payslip_number") val payslipNumber: String? = null,
    val month: String? = null,
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("total_hours") val totalHours: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("gross_pay") val grossPay: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("social_charges") val socialCharges: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("tax_estimate") val taxEstimate: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("net_pay") val netPay: Double? = null,
    val status: String? = null,
)

// ── Employee profile ────────────────────────────────────────────────────────

@Serializable
data class EmployeeDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("phone_mobile") val phoneMobile: String? = null,
    val role: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    @SerialName("weekly_hours") val weeklyHours: Double? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val country: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    val status: String? = null,
    @SerialName("contract_type") val contractType: String? = null,
)
