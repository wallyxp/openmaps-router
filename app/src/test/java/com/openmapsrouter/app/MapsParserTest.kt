package com.openmapsrouter.app

import com.openmapsrouter.app.parser.MapsParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MapsParserTest {

    @Test
    fun testUserReportedShortLink() = runBlocking {
        val url = "https://maps.app.goo.gl/AroemCAc1HY637dS7?g_st=ac"
        try {
            val result = MapsParser.deriveCoordinates(url)
            println("User link result: lat=${result.latitude}, lon=${result.longitude}, name=${result.name}, method=${result.sourceMethod}")
            assertTrue("Latitude should be around 26.12°", result.latitude in 26.0..26.3)
            assertTrue("Longitude should be around 91.78°", result.longitude in 91.5..92.0)
        } catch (e: Exception) {
            println("DEBUG EXCEPTION: " + e.message)
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testDirectUrlWithS2Cell() {
        val url = "https://www.google.com/maps/place/Hill-View+Homestay,+Unnamed+Road,+Navagraha+Hills,+Guwahati,+Assam+781004/data=!4m2!3m1!1s0x375a5932a2e8ec67:0x320ac9eeba86df0b!18m1!1e1"
        val result = MapsParser.parseCoordinatesFromUrlString(url, url)
        assertNotNull(result)
        assertEquals(26.1263, result!!.latitude, 0.001)
        assertEquals(91.7825, result.longitude, 0.001)
        assertEquals("Hill-View Homestay, Unnamed Road, Navagraha Hills, Guwahati, Assam 781004", result.name)
    }

    @Test
    fun testGeoUris() {
        // Standard geo URI
        val res1 = MapsParser.extractDirectCoordinates("geo:37.7749,-122.4194")
        assertNotNull(res1)
        assertEquals(37.7749, res1!!.latitude, 0.0001)
        assertEquals(-122.4194, res1.longitude, 0.0001)

        // Geo with zoom
        val res2 = MapsParser.extractDirectCoordinates("geo:37.7749,-122.4194?z=16")
        assertNotNull(res2)
        assertEquals(37.7749, res2!!.latitude, 0.0001)
        assertEquals(-122.4194, res2.longitude, 0.0001)

        // Geo with 0,0 and q=lat,lon(Label) - standard Google Search preview format
        val res3 = MapsParser.extractDirectCoordinates("geo:0,0?q=37.7749,-122.4194(Golden+Gate+Bridge)")
        assertNotNull(res3)
        assertEquals(37.7749, res3!!.latitude, 0.0001)
        assertEquals(-122.4194, res3.longitude, 0.0001)
        assertEquals("Golden Gate Bridge", res3.name)

        // Geo with lat,lon and text query label
        val res4 = MapsParser.extractDirectCoordinates("geo:37.7749,-122.4194?q=San+Francisco+City+Hall")
        assertNotNull(res4)
        assertEquals(37.7749, res4!!.latitude, 0.0001)
        assertEquals(-122.4194, res4.longitude, 0.0001)
        assertEquals("San Francisco City Hall", res4.name)

        // Google Navigation scheme
        val res5 = MapsParser.extractDirectCoordinates("google.navigation:q=37.7749,-122.4194")
        assertNotNull(res5)
        assertEquals(37.7749, res5!!.latitude, 0.0001)
        assertEquals(-122.4194, res5.longitude, 0.0001)
    }

    @Test
    fun testPlaceSearchSuggestions() = runBlocking {
        val suggestions = com.openmapsrouter.app.search.PlaceSearchService.fetchSuggestions("Starbucks Times Square")
        println("Fetched suggestions count: ${suggestions.size}")
        assertTrue("Should return suggestions for Starbucks Times Square", suggestions.isNotEmpty())
        val first = suggestions.first()
        println("First suggestion: name=${first.name}, address=${first.address}, lat=${first.latitude}, lon=${first.longitude}")
        assertNotNull(first.name)
        assertTrue(first.name.contains("Starbucks", ignoreCase = true))
        assertNotNull(first.address)
        assertTrue(first.address.isNotBlank())
        assertNotNull(first.latitude)
        assertNotNull(first.longitude)
    }
}
