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

    data class GeoAddress(val name: String?, val fullAddress: String?)

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): GeoAddress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (android.location.Geocoder.isPresent()) {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val result = suspendCancellableCoroutine<List<android.location.Address>?> { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                    val addr = result?.firstOrNull()
                    if (addr != null) {
                        val name = addr.featureName?.takeIf { it.isNotBlank() && !it.matches(Regex("""^-?\d+(\.\d+)?$""")) }
                        val fullAddress = addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
                        return@withContext GeoAddress(name = name, fullAddress = fullAddress)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val name = addr.featureName?.takeIf { it.isNotBlank() && !it.matches(Regex("""^-?\d+(\.\d+)?$""")) }
                        val fullAddress = addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
                        return@withContext GeoAddress(name = name, fullAddress = fullAddress)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: Photon reverse API
        try {
            val url = "https://photon.komoot.io/reverse?lat=$latitude&lon=$longitude"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "OpenMapsRouter/1.0 (Android)")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
                        val features = root.getAsJsonArray("features")
                        if (features != null && features.size() > 0) {
                            val first = features[0].asJsonObject
                            val props = first.getAsJsonObject("properties")
                            val name = props.get("name")?.asString
                            val street = props.get("street")?.asString
                            val housenumber = props.get("housenumber")?.asString
                            val city = props.get("city")?.asString ?: props.get("town")?.asString ?: props.get("village")?.asString
                            val state = props.get("state")?.asString
                            val country = props.get("country")?.asString
                            val postcode = props.get("postcode")?.asString

                            val addressParts = listOfNotNull(
                                listOfNotNull(housenumber, street).joinToString(" ").takeIf { it.isNotBlank() },
                                city,
                                state,
                                postcode,
                                country
                            )
                            val fullAddress = addressParts.joinToString(", ").takeIf { it.isNotBlank() }
                            return@withContext GeoAddress(name = name, fullAddress = fullAddress)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext GeoAddress(null, null)
    }
}
