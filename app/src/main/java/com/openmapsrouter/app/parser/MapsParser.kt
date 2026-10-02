package com.openmapsrouter.app.parser

import com.openmapsrouter.app.data.CoordinateResult
import com.openmapsrouter.app.data.SourceMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object MapsParser {

    private val manualClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return !lat.isNaN() && !lon.isNaN() &&
                lat in -90.0..90.0 &&
                lon in -180.0..180.0 &&
                !(lat == 0.0 && lon == 0.0)
    }

    fun cleanPlaceName(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        return try {
            val decoded = URLDecoder.decode(rawName.replace("+", " "), StandardCharsets.UTF_8.name()).trim()
            if (decoded.matches(Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$")) || decoded.isEmpty()) {
                null
            } else {
                decoded
            }
        } catch (e: Exception) {
            rawName.replace("+", " ").trim()
        }
    }

    fun parseGeoOrNavigationUri(input: String): CoordinateResult? {
        val trimmed = input.trim()

        // Match geo:... or google.navigation:... or openmapsrouter:... anywhere in text or at start
        val schemePattern = Pattern.compile("(?:geo|google\\.navigation|openmapsrouter):[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE)
        val schemeMatcher = schemePattern.matcher(trimmed)
        val uriStr = if (schemeMatcher.find()) schemeMatcher.group(0) else {
            if (trimmed.startsWith("geo:", ignoreCase = true) ||
                trimmed.startsWith("google.navigation:", ignoreCase = true) ||
                trimmed.startsWith("openmapsrouter:", ignoreCase = true)
            ) {
                trimmed
            } else {
                return null
            }
        }

        var lat: Double? = null
        var lon: Double? = null
        var name: String? = null

        val decodedUri = try {
            URLDecoder.decode(uriStr, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            uriStr
        }

        // 1. Extract label from parentheses if present, e.g. (Place+Name) or (Place Name)
        val labelPattern = Pattern.compile("\\(([^)]+)\\)")
        val labelMatcher = labelPattern.matcher(decodedUri)
        if (labelMatcher.find()) {
            name = cleanPlaceName(labelMatcher.group(1))
        }

        // 2. Coordinates in query params or navigation scheme: ?q=lat,lon, :q=lat,lon, ?ll=lat,lon
        val qCoordPattern = Pattern.compile(
            "[?&:=](?:q|ll|daddr|destination|center)=(-?\\d+(?:\\.\\d+)?)[,\\+](-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
        )
        val qMatcher = qCoordPattern.matcher(decodedUri)
        if (qMatcher.find()) {
            val qLat = qMatcher.group(1)?.toDoubleOrNull()
            val qLon = qMatcher.group(2)?.toDoubleOrNull()
            if (qLat != null && qLon != null && isValidCoordinate(qLat, qLon)) {
                lat = qLat
                lon = qLon
            }
        }

        // 3. Coordinates directly in scheme path: geo:lat,lon or google.navigation:lat,lon or openmapsrouter://map?ll=lat,lon
        if (lat == null || lon == null) {
            val baseCoordPattern = Pattern.compile(
                "(?:geo|google\\.navigation|openmapsrouter):/?/?(-?\\d+(?:\\.\\d+)?)[,\\s]+(-?\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE
            )
            val baseMatcher = baseCoordPattern.matcher(decodedUri)
            if (baseMatcher.find()) {
                val bLat = baseMatcher.group(1)?.toDoubleOrNull()
                val bLon = baseMatcher.group(2)?.toDoubleOrNull()
                if (bLat != null && bLon != null && isValidCoordinate(bLat, bLon)) {
                    lat = bLat
                    lon = bLon
                }
            }
        }

        // 4. If name was not in parentheses, but there's a text ?q=Name query param (when lat,lon came from geo:lat,lon)
        if (lat != null && lon != null && name == null) {
            val textQPattern = Pattern.compile("[?&:=]q=([^&()]+)", Pattern.CASE_INSENSITIVE)
            val textQMatcher = textQPattern.matcher(decodedUri)
            if (textQMatcher.find()) {
                val candidate = cleanPlaceName(textQMatcher.group(1))
                if (candidate != null && !candidate.matches(Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$"))) {
                    name = candidate
                }
            }
        }

        if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
            return CoordinateResult(
                latitude = lat,
                longitude = lon,
                name = name,
                sourceMethod = SourceMethod.DIRECT_COORDS,
                originalUrl = trimmed
            )
        }

        return null
    }

    fun extractDirectCoordinates(input: String): CoordinateResult? {
        val trimmed = input.trim()

        // 1. Check geo:, google.navigation:, or openmapsrouter: URIs
        val fromUri = parseGeoOrNavigationUri(trimmed)
        if (fromUri != null) return fromUri

        // 2. Raw coordinates: 37.774929, -122.419416 or 37.774929 -122.419416
        val rawPattern = Pattern.compile("^(-?\\d{1,2}(?:\\.\\d+)?)[,\\s]+(-?\\d{1,3}(?:\\.\\d+)?)$")
        val rawMatcher = rawPattern.matcher(trimmed)
        if (rawMatcher.find()) {
            val lat = rawMatcher.group(1)?.toDoubleOrNull()
            val lon = rawMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return CoordinateResult(
                    latitude = lat,
                    longitude = lon,
                    sourceMethod = SourceMethod.DIRECT_COORDS,
                    originalUrl = trimmed
                )
            }
        }

        return null
    }

    fun extractUrlFromText(text: String): String? {
        val pattern = Pattern.compile("(https?://[^\\s<>\"'{}|\\\\^`]+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractNameFromUrl(url: String): String? {
        val placePattern = Pattern.compile("/place/([^/@?#]+)", Pattern.CASE_INSENSITIVE)
        val placeMatcher = placePattern.matcher(url)
        if (placeMatcher.find()) {
            val raw = placeMatcher.group(1)
            val cleaned = cleanPlaceName(raw)
            if (cleaned != null) return cleaned
        }

        val queryPattern = Pattern.compile("[?&]q=([^&@#]+)", Pattern.CASE_INSENSITIVE)
        val queryMatcher = queryPattern.matcher(url)
        if (queryMatcher.find()) {
            val candidate = cleanPlaceName(queryMatcher.group(1))
            if (candidate != null && !candidate.matches(Regex("^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$"))) {
                return candidate
            }
        }
        return null
    }

    fun parseCoordinatesFromUrlString(url: String, originalUrl: String): CoordinateResult? {
        var lat: Double? = null
        var lon: Double? = null
        var sourceMethod = SourceMethod.CAMERA_VIEW
        val placeName = extractNameFromUrl(url)

        // 1. Exact Pin coordinates: !3d<lat>!4d<lon>
        val pinPattern = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?).*?!4d(-?\\d+(?:\\.\\d+)?)")
        val pinMatcher = pinPattern.matcher(url)
        if (pinMatcher.find()) {
            val pLat = pinMatcher.group(1)?.toDoubleOrNull()
            val pLon = pinMatcher.group(2)?.toDoubleOrNull()
            if (pLat != null && pLon != null && isValidCoordinate(pLat, pLon)) {
                lat = pLat
                lon = pLon
                sourceMethod = SourceMethod.PIN_EXACT
            }
        }

        // 2. Google Maps S2 Cell ID in place URL: !1s(0x[0-9a-fA-F]+):0x[0-9a-fA-F]+
        if (lat == null || lon == null) {
            val s2Pattern = Pattern.compile("(?:!1s|!3m1!1s)?(0x[0-9a-fA-F]{12,16}):0x[0-9a-fA-F]+")
            val s2Matcher = s2Pattern.matcher(url)
            if (s2Matcher.find()) {
                val hexCellId = s2Matcher.group(1)
                if (hexCellId != null) {
                    val decoded = S2Geometry.decodeCellIdHex(hexCellId)
                    if (decoded != null && isValidCoordinate(decoded.lat, decoded.lon)) {
                        lat = decoded.lat
                        lon = decoded.lon
                        sourceMethod = SourceMethod.PIN_EXACT
                    }
                }
            }
        }

        // 3. Camera viewpoint: /@<lat>,<lon>
        if (lat == null || lon == null) {
            val atPattern = Pattern.compile("@(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)")
            val atMatcher = atPattern.matcher(url)
            if (atMatcher.find()) {
                val aLat = atMatcher.group(1)?.toDoubleOrNull()
                val aLon = atMatcher.group(2)?.toDoubleOrNull()
                if (aLat != null && aLon != null && isValidCoordinate(aLat, aLon)) {
                    lat = aLat
                    lon = aLon
                    sourceMethod = SourceMethod.CAMERA_VIEW
                }
            }
        }

        // 4. Query params: q=lat,lon or query=lat,lon or ll=lat,lon or daddr=lat,lon
        if (lat == null || lon == null) {
            val qPattern = Pattern.compile(
                "[?&](?:q|query|ll|sll|daddr|saddr|destination|origin|center)=(-?\\d+(?:\\.\\d+)?)[,\\+](-?\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE
            )
            val qMatcher = qPattern.matcher(url)
            if (qMatcher.find()) {
                val qLat = qMatcher.group(1)?.toDoubleOrNull()
                val qLon = qMatcher.group(2)?.toDoubleOrNull()
                if (qLat != null && qLon != null && isValidCoordinate(qLat, qLon)) {
                    lat = qLat
                    lon = qLon
                    sourceMethod = SourceMethod.QUERY_PARAM
                }
            }
        }

        // 5. Query with place@lat,lon: ?q=Place+Name@lat,lon
        if (lat == null || lon == null) {
            val atQueryPattern = Pattern.compile("[?&]q=[^&]*@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
            val atQueryMatcher = atQueryPattern.matcher(url)
            if (atQueryMatcher.find()) {
                val aqLat = atQueryMatcher.group(1)?.toDoubleOrNull()
                val aqLon = atQueryMatcher.group(2)?.toDoubleOrNull()
                if (aqLat != null && aqLon != null && isValidCoordinate(aqLat, aqLon)) {
                    lat = aqLat
                    lon = aqLon
                    sourceMethod = SourceMethod.QUERY_PARAM
                }
            }
        }

        // 6. Path coordinates: /search/lat,lon or /dir//lat,lon
        if (lat == null || lon == null) {
            val pathPattern = Pattern.compile("/(?:search|dir/?/?)?/?(-?\\d+(?:\\.\\d+)?),\\s*\\+?(-?\\d+(?:\\.\\d+)?)")
            val pathMatcher = pathPattern.matcher(url)
            if (pathMatcher.find()) {
                val ptLat = pathMatcher.group(1)?.toDoubleOrNull()
                val ptLon = pathMatcher.group(2)?.toDoubleOrNull()
                if (ptLat != null && ptLon != null && isValidCoordinate(ptLat, ptLon)) {
                    lat = ptLat
                    lon = ptLon
                    sourceMethod = SourceMethod.QUERY_PARAM
                }
            }
        }

        if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
            return CoordinateResult(
                latitude = lat,
                longitude = lon,
                name = placeName,
                sourceMethod = sourceMethod,
                originalUrl = originalUrl,
                resolvedUrl = if (url != originalUrl) url else null
            )
        }

        return null
    }

    fun parseCoordinatesFromHtml(html: String, originalUrl: String, resolvedUrl: String?): CoordinateResult? {
        // 1. S2 Cell ID in HTML scripts or JSON state: "0x375a5932a2e8ec67:0x320ac9eeba86df0b"
        val s2HtmlPattern = Pattern.compile("[\"'](0x[0-9a-fA-F]{12,16}):0x[0-9a-fA-F]+[\"']")
        val s2HtmlMatcher = s2HtmlPattern.matcher(html)
        if (s2HtmlMatcher.find()) {
            val hexCellId = s2HtmlMatcher.group(1)
            if (hexCellId != null) {
                val decoded = S2Geometry.decodeCellIdHex(hexCellId)
                if (decoded != null && isValidCoordinate(decoded.lat, decoded.lon)) {
                    return CoordinateResult(
                        latitude = decoded.lat,
                        longitude = decoded.lon,
                        sourceMethod = SourceMethod.PIN_EXACT,
                        originalUrl = originalUrl,
                        resolvedUrl = resolvedUrl
                    )
                }
            }
        }

        // 2. Staticmap center
        val centerPattern = Pattern.compile("staticmap\\?[^\"'>]*center=(-?\\d+(?:\\.\\d+)?)(?:%2C|,)(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        val centerMatcher = centerPattern.matcher(html)
        if (centerMatcher.find()) {
            val lat = centerMatcher.group(1)?.toDoubleOrNull()
            val lon = centerMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return CoordinateResult(
                    latitude = lat,
                    longitude = lon,
                    sourceMethod = SourceMethod.HTML_META,
                    originalUrl = originalUrl,
                    resolvedUrl = resolvedUrl
                )
            }
        }

        // 3. Staticmap markers
        val markerPattern = Pattern.compile("staticmap\\?[^\"'>]*markers=(?:[^&]*%7C)?(-?\\d+(?:\\.\\d+)?)(?:%2C|,)(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        val markerMatcher = markerPattern.matcher(html)
        if (markerMatcher.find()) {
            val lat = markerMatcher.group(1)?.toDoubleOrNull()
            val lon = markerMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return CoordinateResult(
                    latitude = lat,
                    longitude = lon,
                    sourceMethod = SourceMethod.HTML_META,
                    originalUrl = originalUrl,
                    resolvedUrl = resolvedUrl
                )
            }
        }

        // 4. og:url or canonical link
        val ogPattern = Pattern.compile("<meta[^>]+property=[\"']og:url[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ogMatcher = ogPattern.matcher(html)
        if (ogMatcher.find()) {
            val link = ogMatcher.group(1)
            if (link != null) {
                val fromOg = parseCoordinatesFromUrlString(link, originalUrl)
                if (fromOg != null) return fromOg.copy(resolvedUrl = link)
            }
        }

        // 5. Protobuf in scripts: !3d<lat>!4d<lon>
        val protoPattern = Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?).*?!4d(-?\\d+(?:\\.\\d+)?)")
        val protoMatcher = protoPattern.matcher(html)
        if (protoMatcher.find()) {
            val lat = protoMatcher.group(1)?.toDoubleOrNull()
            val lon = protoMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return CoordinateResult(
                    latitude = lat,
                    longitude = lon,
                    sourceMethod = SourceMethod.PIN_EXACT,
                    originalUrl = originalUrl,
                    resolvedUrl = resolvedUrl
                )
            }
        }

        return null
    }

    suspend fun deriveCoordinates(rawInput: String): CoordinateResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Input is empty. Please enter or paste a Google Maps link.")
        }

        // 1. Direct coordinates
        val direct = extractDirectCoordinates(trimmed)
        if (direct != null) return@withContext direct

        // 2. Extract URL or fallback from geo: query
        var targetUrl = extractUrlFromText(trimmed)
        if (targetUrl == null) {
            if (trimmed.matches(Regex("^(maps\\.app\\.goo\\.gl|goo\\.gl|maps\\.google\\.|www\\.google\\.com/maps).*", RegexOption.IGNORE_CASE))) {
                targetUrl = "https://$trimmed"
            } else if (trimmed.startsWith("geo:", ignoreCase = true) || trimmed.startsWith("google.navigation:", ignoreCase = true)) {
                val qPattern = Pattern.compile("[?&]q=([^&]+)", Pattern.CASE_INSENSITIVE)
                val qMatcher = qPattern.matcher(trimmed)
                if (qMatcher.find()) {
                    val query = qMatcher.group(1).orEmpty()
                    val cleaned = cleanPlaceName(query) ?: query
                    targetUrl = "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(cleaned, StandardCharsets.UTF_8.name())
                } else {
                    throw IllegalArgumentException("Could not find valid coordinates in the geo link.")
                }
            } else {
                throw IllegalArgumentException("Could not find a valid Google Maps link or coordinates in the provided text.")
            }
        }

        // 3. Fast direct check if URL already has coordinates and isn't a short link
        val directFromUrl = parseCoordinatesFromUrlString(targetUrl, targetUrl)
        val isShortLink = targetUrl.contains("maps.app.goo.gl", ignoreCase = true) ||
                targetUrl.contains("goo.gl/maps", ignoreCase = true) ||
                targetUrl.contains("bit.ly", ignoreCase = true) ||
                targetUrl.contains("tinyurl.com", ignoreCase = true)

        if (directFromUrl != null && !isShortLink) {
            return@withContext directFromUrl
        }

        val safeTargetUrl = targetUrl ?: throw IllegalArgumentException("Target URL cannot be null")
        var currentUrl: String = safeTargetUrl
        var finalResult: CoordinateResult? = null

        try {
            for (hop in 0 until 6) {
                val requestBuilder = Request.Builder().url(currentUrl)

                if (!currentUrl.contains("maps.app.goo.gl", ignoreCase = true)) {
                    requestBuilder.header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
                }

                manualClient.newCall(requestBuilder.build()).execute().use { response ->
                    val code = response.code
                    val locationHeader = response.header("Location")

                    if (locationHeader != null && (code in 300..399)) {
                        val fromLocation = parseCoordinatesFromUrlString(locationHeader, safeTargetUrl)
                        if (fromLocation != null) {
                            finalResult = fromLocation.copy(resolvedUrl = locationHeader)
                            return@use
                        }
                        currentUrl = locationHeader
                    } else if (code in 200..299) {
                        val fromCurrentUrl = parseCoordinatesFromUrlString(currentUrl, safeTargetUrl)
                        if (fromCurrentUrl != null) {
                            finalResult = fromCurrentUrl.copy(resolvedUrl = currentUrl)
                            return@use
                        }

                        val bodyText = response.body?.string().orEmpty()
                        val fromHtml = parseCoordinatesFromHtml(bodyText, safeTargetUrl, currentUrl)
                        if (fromHtml != null) {
                            var title = fromHtml.name
                            if (title == null) {
                                val titlePattern = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
                                val titleMatcher = titlePattern.matcher(bodyText)
                                if (titleMatcher.find()) {
                                    val candidate = titleMatcher.group(1)
                                        ?.replace(" - Google Maps", "", ignoreCase = true)
                                        ?.replace(" - Google", "", ignoreCase = true)
                                        ?.trim()
                                    if (!candidate.isNullOrBlank() && !candidate.equals("Google Maps", ignoreCase = true)) {
                                        title = candidate
                                    }
                                }
                            }
                            finalResult = fromHtml.copy(name = title)
                            return@use
                        }
                    }
                }

                if (finalResult != null) {
                    return@withContext finalResult!!
                }
            }

            if (finalResult != null) {
                return@withContext finalResult!!
            }

            if (directFromUrl != null) {
                return@withContext directFromUrl
            }

            throw IllegalStateException("Could not extract latitude and longitude from the resolved Google Maps page.")
        } catch (e: Exception) {
            if (finalResult != null) return@withContext finalResult!!
            if (directFromUrl != null) return@withContext directFromUrl
            throw e
        }
    }
}
