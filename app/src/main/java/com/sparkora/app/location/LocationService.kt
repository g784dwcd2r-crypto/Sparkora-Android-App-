package com.sparkora.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationService(private val context: Context) : LocationProvider {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * One-shot high-accuracy fix, falling back to the last known location.
     * Returns null if permission is missing or no fix arrives within 15s.
     */
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val fix: Location? = withTimeoutOrNull(15_000L) {
            try {
                fused.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token,
                ).await() ?: fused.lastLocation.await()
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
        return fix?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
