package com.openmapsrouter.app.data

enum class SourceMethod(val label: String) {
    PIN_EXACT("Exact Pin (!3d/!4d)"),
    CAMERA_VIEW("Camera Viewport (@lat,lon)"),
    QUERY_PARAM("Query Param (?q/ll)"),
    HTML_META("HTML StaticMap Meta"),
    DIRECT_COORDS("Direct Coordinates")
}

data class CoordinateResult(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val sourceMethod: SourceMethod,
    val originalUrl: String,
    val resolvedUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val sourceMethod: SourceMethod,
    val originalUrl: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toCoordinateResult(): CoordinateResult {
        return CoordinateResult(
            latitude = latitude,
            longitude = longitude,
            name = name,
            sourceMethod = sourceMethod,
            originalUrl = originalUrl,
            timestamp = timestamp
        )
    }
}

data class AppSettings(
    val autoOpenOrganicMaps: Boolean = false,
    val preferredScheme: String = "om", // "om" or "geo"
    val keepHistory: Boolean = true
)
