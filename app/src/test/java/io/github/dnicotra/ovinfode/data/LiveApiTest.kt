package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.LatLon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Smoke test against the real Transitous API. Skipped by default; run with
 * `./gradlew testDebugUnitTest -PliveApi --tests '*LiveApiTest'`.
 */
class LiveApiTest {
    private val repository = TransitRepository(TransitousApi(userAgent = "Abfahrten-tests/0.1"))

    @Before
    fun onlyWhenRequested() = assumeTrue("live API tests disabled", System.getProperty("liveApi") == "true")

    @Test
    fun `search, departures and trip work end to end`() = runBlocking {
        val stop = repository.searchStops("Köln Hbf", near = null).first()
        println("stop: ${stop.name} (${stop.id})")

        val board = repository.departures(stop, count = 20)
        println("departures: ${board.departures.size}, real-time: ${board.departures.count { it.isRealtime }}, cancelled: ${board.departures.count { it.cancelled }}")
        assertTrue(board.departures.isNotEmpty())

        val trip = repository.trip(board.departures.first().tripId)
        println("trip: ${trip.line.name} → ${trip.headsign}, ${trip.stops.size} stops")
        assertTrue(trip.stops.size >= 2)
    }

    @Test
    fun `nearby stops around Cologne cathedral`() = runBlocking {
        val nearby = repository.nearbyStops(LatLon(50.9420, 6.9580))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.any { it.stop.name.contains("Dom") || it.stop.name.contains("Hauptbahnhof") })
    }
}
