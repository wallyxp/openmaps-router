package com.openmapsrouter.app.search

import com.google.gson.JsonParser
import com.openmapsrouter.app.data.CoordinateResult
import com.openmapsrouter.app.data.SourceMethod
import com.openmapsrouter.app.parser.MapsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class PlaceSuggestion(
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeId: String? = null
)

object PlaceSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetchSuggestions(
        query: String,
        apiKey: String? = null,
        limit: Int = 7
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. If user provided a Google Places API key, try Google Places Autocomplete first
        if (!apiKey.isNullOrBlank()) {
            try {
                val googleResults = fetchGooglePlacesSuggestions(trimmed, apiKey)
                if (googleResults.isNotEmpty()) {
                    return@withContext googleResults
                }
            } catch (e: Exception) {
                // Fall back to Photon
            }
        }

        // 2. Default: Fetch from Photon (OpenStreetMap/Komoot geocoder)
        // High speed, free, no API key needed, returns Place Name + Address + exact coords
        return@withContext fetchPhotonSuggestions(trimmed, limit)
    }

    private fun fetchPhotonSuggestions(query: String, limit: Int): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://photon.komoot.io/api/?q=$encoded&limit=$limit"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OpenMapsRouter/1.0 (Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val body = response.body?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject
                val features = json.getAsJsonArray("features") ?: return emptyList()

                val suggestions = mutableListOf<PlaceSuggestion>()
                for (element in features) {
                    val feature = element.asJsonObject
                    val properties = feature.getAsJsonObject("properties") ?: continue
                    val geometry = feature.getAsJsonObject("geometry")
                    val coordinates = geometry?.getAsJsonArray("coordinates")

                    val rawName = properties.get("name")?.asString?.ifBlank { null }
                        ?: properties.get("street")?.asString?.ifBlank { null }
                        ?: continue

                    val name = MapsParser.cleanPlaceName(rawName) ?: rawName

                    val addressParts = mutableListOf<String>()
                    val housenumber = properties.get("housenumber")?.asString?.ifBlank { null }
                    val street = properties.get("street")?.asString?.ifBlank { null }
                    if (street != null && street != name) {
                        if (housenumber != null) {
                            addressParts.add("$housenumber $street")
                        } else {
                            addressParts.add(street)
                        }
                    }

                    val locality = properties.get("locality")?.asString?.ifBlank { null }
                    if (locality != null && locality != name && locality !in addressParts) addressParts.add(locality)

                    val district = properties.get("district")?.asString?.ifBlank { null }
                    if (district != null && district != name && district !in addressParts) addressParts.add(district)

                    val city = properties.get("city")?.asString?.ifBlank { null }
                    if (city != null && city != name && city !in addressParts) addressParts.add(city)

                    val state = properties.get("state")?.asString?.ifBlank { null }
                    if (state != null && state != name && state !in addressParts) addressParts.add(state)

                    val country = properties.get("country")?.asString?.ifBlank { null }
                    if (country != null && country !in addressParts) addressParts.add(country)

                    val address = if (addressParts.isNotEmpty()) {
                        addressParts.joinToString(", ")
                    } else {
                        properties.get("country")?.asString ?: "Known Location"
                    }

                    val lon = coordinates?.get(0)?.asDouble
                    val lat = coordinates?.get(1)?.asDouble

                    suggestions.add(
                        PlaceSuggestion(
                            name = name,
                            address = address,
                            latitude = if (lat != null && !lat.isNaN()) lat else null,
                            longitude = if (lon != null && !lon.isNaN()) lon else null
                        )
                    )
                }

                return suggestions
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun fetchGooglePlacesSuggestions(query: String, apiKey: String): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encoded&key=$apiKey"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string().orEmpty()
            val json = JsonParser.parseString(body).asJsonObject
            val predictions = json.getAsJsonArray("predictions") ?: return emptyList()

            val suggestions = mutableListOf<PlaceSuggestion>()
            for (element in predictions) {
                val item = element.asJsonObject
                val structured = item.getAsJsonObject("structured_formatting")
                val placeId = item.get("place_id")?.asString?.ifBlank { null }

                val name = structured?.get("main_text")?.asString?.ifBlank { null }
                    ?: item.get("description")?.asString.orEmpty()
                val address = structured?.get("secondary_text")?.asString?.ifBlank { null }
                    ?: item.get("description")?.asString.orEmpty()

                suggestions.add(
                    PlaceSuggestion(
                        name = name,
                        address = address,
                        placeId = placeId
                    )
                )
            }
            return suggestions
        }
    }

    suspend fun resolvePlaceDetails(
        suggestion: PlaceSuggestion,
        apiKey: String? = null
    ): CoordinateResult = withContext(Dispatchers.IO) {
        // 1. Direct coordinates available from Photon/OSM
        if (suggestion.latitude != null && suggestion.longitude != null) {
            return@withContext CoordinateResult(
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                name = suggestion.name,
                sourceMethod = SourceMethod.DIRECT_COORDS,
                originalUrl = "${suggestion.name}, ${suggestion.address}"
            )
        }

        // 2. Google Place ID Details
        if (!suggestion.placeId.isNullOrBlank() && !apiKey.isNullOrBlank()) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=${suggestion.placeId}&fields=geometry,name,formatted_address&key=$apiKey"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JsonParser.parseString(body).asJsonObject
                        val result = json.getAsJsonObject("result")
                        val geometry = result?.getAsJsonObject("geometry")
                        val location = geometry?.getAsJsonObject("location")
                        val lat = location?.get("lat")?.asDouble
                        val lon = location?.get("lng")?.asDouble
                        val formattedAddress = result?.get("formatted_address")?.asString

                        if (lat != null && lon != null && !lat.isNaN() && !lon.isNaN()) {
                            return@withContext CoordinateResult(
                                latitude = lat,
                                longitude = lon,
                                name = suggestion.name,
                                sourceMethod = SourceMethod.PIN_EXACT,
                                originalUrl = formattedAddress ?: suggestion.name
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        // 3. Fallback: Query Google Maps search directly
        val query = "${suggestion.name} ${suggestion.address}".trim()
        return@withContext MapsParser.deriveCoordinates(
            "https://www.google.com/search?tbm=map&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        )
    }
}
