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
}
