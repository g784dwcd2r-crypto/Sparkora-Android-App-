package com.sparkora.app.location

/** Plain coordinates, so the location contract has no Android framework types. */
data class GeoPoint(val lat: Double, val lng: Double)

interface LocationProvider {
    fun hasPermission(): Boolean

    /** One-shot fix, or null when permission is missing / no fix arrives in time. */
    suspend fun currentLocation(): GeoPoint?
}
