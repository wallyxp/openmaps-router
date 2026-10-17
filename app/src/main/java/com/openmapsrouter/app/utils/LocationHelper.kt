package com.openmapsrouter.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // 1. Try last known location first if very recent (< 1 minute old)
        val oneMinuteAgo = System.currentTimeMillis() - 60 * 1000
        var bestLastKnown: Location? = null
        for (provider in locationManager.getProviders(true)) {
            val loc = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLastKnown == null || loc.time > bestLastKnown.time) {
                bestLastKnown = loc
            }
        }
        if (bestLastKnown != null && bestLastKnown.time > oneMinuteAgo) {
            return bestLastKnown
        }

        // 2. Request fresh location using LocationManagerCompat with a 10s timeout
        val freshLocation = withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }

                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> LocationManager.PASSIVE_PROVIDER
                }

                try {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(bestLastKnown)
                    }
                }
            }
        }

        return freshLocation ?: bestLastKnown
    }
}
