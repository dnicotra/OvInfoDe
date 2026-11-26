package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.api.MatchDto
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MappingTest {

    @Test
    fun `berlin departures carry real-time delays, colours and platforms`() {
        val result = Fixtures.berlin
        assertEquals(16, result.departures.size)
        assertEquals("de-DELFI_de:11000:900100003", result.stop?.id)
        assertNotNull(result.nextPageCursor)

        val s3 = result.departures[0]
        assertEquals("S3", s3.line.name)
        assertEquals(TransportMode.SUBURBAN, s3.line.mode)
        assertEquals(0xFF0066AD.toInt(), s3.line.color)
        assertEquals(0xFFFFFFFF.toInt(), s3.line.textColor)
        assertEquals("S Charlottenburg Bhf (Berlin)", s3.headsign)
        assertEquals("S-Bahn Berlin GmbH", s3.operator)
        assertTrue(s3.isRealtime)
        assertEquals(0L, s3.delayMinutes)
        // No track field: taken from the description "S-Bahnsteig Gleis 4".
        assertEquals("4", s3.platform)

        val s9 = result.departures[1]
        assertEquals("S9", s9.line.name)
        assertEquals(3L, s9.delayMinutes)
        assertEquals(Instant.parse("2026-11-26T06:05:00Z"), s9.time)

        val u2 = result.departures[2]
        assertEquals(TransportMode.SUBWAY, u2.line.mode)
        assertEquals("1 (U2)", u2.platform)
        assertFalse(u2.platformChanged)
    }

    @Test
    fun `koeln board has cancellations and trains without real-time data`() {
        val departures = Fixtures.koeln.departures

        val ic = departures.first { it.line.name == "IC 2443" }
        assertTrue(ic.cancelled)
        assertFalse(ic.isRealtime)
        assertNull(ic.delayMinutes)
        assertEquals(TransportMode.LONG_DISTANCE, ic.line.mode)
        assertNull("no route colour in the feed", ic.line.color)
        assertEquals("4 A-C", ic.platform)

        assertTrue("whole trip cancelled", departures.first { it.line.name == "S12" }.cancelled)
        assertEquals(TransportMode.LONG_DISTANCE, departures.first { it.line.name == "ICE 206" }.line.mode)
        assertEquals(30L, departures.first { it.line.name == "S19" }.delayMinutes)

        val bus = departures.first { it.line.name == "133" }
        assertEquals(TransportMode.BUS, bus.line.mode)
        assertEquals("3", bus.platform)
        assertEquals(TransportMode.TRAM, departures.first { it.line.name == "16" }.line.mode)
    }

    @Test
    fun `trip contains every stop with real-time times`() {
        val trip = Fixtures.tripS3
        assertEquals(23, trip.stops.size)
        assertEquals("S3", trip.line.name)
        assertEquals("S Charlottenburg Bhf (Berlin)", trip.headsign)
        assertEquals("S-Bahn Berlin GmbH", trip.operator)
        assertEquals("3726", trip.tripNumber)
        assertTrue(trip.isRealtime)
        assertFalse(trip.cancelled)
        assertEquals("S Erkner Bhf", trip.stops.first().name)
        assertEquals("S Charlottenburg Bhf (Berlin)", trip.stops.last().name)

        val ostbahnhof = trip.stops[12]
        assertEquals(Instant.parse("2026-11-26T05:57:00Z"), ostbahnhof.scheduledArrival)
        assertEquals(Instant.parse("2026-11-26T05:59:00Z"), ostbahnhof.arrival)
        assertEquals("11", ostbahnhof.platform)
    }

    @Test
    fun `geocode matches become stops with town and modes`() {
        val stops = TransitousApi.json.decodeFromString<List<MatchDto>>(Fixtures.text("geocode_alexanderplatz.json")).map { it.toStop() }
        val alex = stops.first()
        assertEquals("S+U Alexanderplatz Bhf (Berlin)", alex.name)
        assertEquals("Berlin", alex.area)
        assertTrue(alex.modes.containsAll(listOf(TransportMode.SUBURBAN, TransportMode.SUBWAY)))
        assertEquals("Krefeld", stops.single { it.name == "Krefeld Alexanderplatz" }.area)
    }

    @Test
    fun `colours and timestamps are parsed defensively`() {
        assertEquals(0xFF0066AD.toInt(), parseColor("0066ad"))
        assertEquals(0xFF0066AD.toInt(), parseColor("#0066AD"))
        assertNull(parseColor(""))
        assertNull(parseColor("xyz"))
        assertNull(parseColor(null))
        assertEquals(Instant.parse("2026-11-26T06:05:00Z"), parseInstant("2026-11-26T06:05:00Z"))
        assertEquals(Instant.parse("2026-11-26T06:05:00Z"), parseInstant("2026-11-26T07:05:00+01:00"))
        assertNull(parseInstant("tomorrow"))
    }

    @Test
    fun `api modes fold into display modes`() {
        assertEquals(TransportMode.LONG_DISTANCE, TransportMode.fromApi("HIGHSPEED_RAIL"))
        assertEquals(TransportMode.LONG_DISTANCE, TransportMode.fromApi("NIGHT_RAIL"))
        assertEquals(TransportMode.REGIONAL, TransportMode.fromApi("REGIONAL_FAST_RAIL"))
        assertEquals(TransportMode.SUBWAY, TransportMode.fromApi("METRO"))
        assertEquals(TransportMode.ON_DEMAND, TransportMode.fromApi("ODM"))
        assertEquals(TransportMode.OTHER, TransportMode.fromApi("SOMETHING_NEW"))
    }
}
