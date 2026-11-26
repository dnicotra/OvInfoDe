package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogicTest {
    private val t0 = Instant.parse("2026-11-26T06:00:00Z")

    @Test
    fun `countdown rounds to minutes and switches to clock time after an hour`() {
        assertEquals(Countdown.Now, countdown(t0.plusSeconds(10), t0))
        assertEquals(Countdown.Now, countdown(t0.minusSeconds(20), t0))
        assertEquals(Countdown.Minutes(1), countdown(t0.plusSeconds(45), t0))
        assertEquals(Countdown.Minutes(2), countdown(t0.plusSeconds(105), t0))
        assertEquals(Countdown.Minutes(59), countdown(t0.plusSeconds(59 * 60), t0))
        assertEquals(Countdown.Clock(t0.plusSeconds(3600)), countdown(t0.plusSeconds(3600), t0))
    }

    @Test
    fun `upcoming drops departures that have left`() {
        val departures = Fixtures.berlin.departures
        val now = Instant.parse("2026-11-26T06:06:00Z")
        val upcoming = departures.upcoming(now)
        assertTrue(upcoming.all { !it.time.isBefore(now.minusSeconds(30)) })
        assertEquals(departures.count { !it.time.isBefore(now.minusSeconds(30)) }, upcoming.size)
        assertTrue(upcoming.size < departures.size)
    }

    @Test
    fun `grouping by line keeps directions apart`() {
        val groups = Fixtures.berlin.departures.groupByLine()
        val u2 = groups.filter { it.first.line.name == "U2" }
        assertTrue(u2.map { it.first.headsign }.containsAll(listOf("U Theodor-Heuss-Platz (Berlin)", "S+U Pankow (Berlin)")))
        assertEquals(u2.size, u2.map { it.first.headsign }.distinct().size)
        // Sorted by the next departure of each group.
        assertEquals(groups.map { it.first.time }.sorted(), groups.map { it.first.time })
    }

    @Test
    fun `vehicle position follows the stop times`() {
        val stops = Fixtures.tripS3.stops
        fun at(time: String) = vehiclePosition(stops, Instant.parse(time))
        assertEquals(VehiclePosition.NotStarted, at("2026-11-26T05:20:00Z"))
        assertEquals(VehiclePosition.Between(0), at("2026-11-26T05:25:00Z"))
        // Alexanderplatz (index 14): arrives 06:04, departs 06:05.
        assertEquals(VehiclePosition.AtStop(14), at("2026-11-26T06:04:30Z"))
        assertEquals(VehiclePosition.Between(14), at("2026-11-26T06:05:30Z"))
        assertEquals(VehiclePosition.Finished, at("2026-11-26T06:30:00Z"))
    }

    @Test
    fun `stop of origin is found by id, then by name`() {
        val stops = Fixtures.tripS3.stops
        assertEquals(14, indexOfStop(stops, "de-VBB_de:11000:900100003:2:53", null))
        assertEquals(14, indexOfStop(stops, "unknown", "S+U Alexanderplatz Bhf (Berlin)"))
        assertEquals(-1, indexOfStop(stops, null, null))
    }

    @Test
    fun `distances and bounding boxes`() {
        val berlin = LatLon(52.52151, 13.411267)
        val koeln = LatLon(50.9420, 6.9580)
        val d = distanceMeters(berlin, koeln)
        assertTrue("was $d", d in 470_000.0..485_000.0)

        val (min, max) = boundingBox(koeln, 500)
        assertTrue(min.lat < koeln.lat && min.lon < koeln.lon && max.lat > koeln.lat && max.lon > koeln.lon)
        assertEquals(500.0, distanceMeters(koeln, LatLon(max.lat, koeln.lon)), 1.0)
        assertEquals(500.0, distanceMeters(koeln, LatLon(koeln.lat, max.lon)), 1.0)
    }
}
