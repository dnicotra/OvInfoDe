package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.districtOf
import io.github.dnicotra.ovinfode.util.indexOfStop
import io.github.dnicotra.ovinfode.util.vehiclePosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Checks the app's data layer against the real Transitous API (plain JVM, no emulator).
 * Skipped by default; run with
 * `./gradlew testDebugUnitTest -PliveApi --tests '*LiveApiTest'`.
 * Keep it small: Transitous is a volunteer-run service.
 */
class LiveApiTest {
    private val repository = TransitRepository(TransitousApi(userAgent = "Abfahrten-tests/0.1"))

    /** Destination names that say nothing without a town. */
    private val bareNames = setOf("hauptbahnhof", "hbf", "bahnhof", "bf", "zob", "busbahnhof")

    private val stations = listOf(
        "Berlin Hbf", "Hamburg Hbf", "München Hbf", "Köln Hbf",
        "Frankfurt (Main) Hbf", "Stuttgart Hbf", "Heidelberg Hbf", "Eberswalde Hbf",
    )

    @Before
    fun onlyWhenRequested() = assumeTrue("live API tests disabled", System.getProperty("liveApi") == "true")

    @Test
    fun `departure boards across Germany load with readable destinations`() = runBlocking {
        val problems = mutableListOf<String>()
        for (query in stations) {
            val stop = repository.searchStops(query, near = null).firstOrNull()
            if (stop == null) {
                problems += "$query: not found"
                continue
            }
            val departures = repository.departures(stop, count = 30).departures
            val realtime = if (departures.isEmpty()) 0 else departures.count { it.isRealtime } * 100 / departures.size
            println(String.format("%-36s %3d departures  %3d%% real-time  %2d cancelled  modes %s",
                stop.name.take(36), departures.size, realtime, departures.count { it.cancelled },
                departures.map { it.line.mode }.distinct().sorted()))

            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            departures.filter { it.headsign.isBlank() }.forEach { problems += "${stop.name}: ${it.line.name} has no destination" }
            // A bare "Hauptbahnhof" is only acceptable for destinations in the same city.
            departures
                .filter { it.headsign.trim().lowercase() in bareNames && districtOf(it.destination?.id) != districtOf(stop.id) }
                .forEach { problems += "${stop.name}: ${it.line.name} → '${it.headsign}' (${it.destination?.id})" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `long-distance trip has its stops, times and the stop we came from`() = runBlocking {
        val stop = repository.searchStops("Frankfurt (Main) Hbf", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.mode == TransportMode.LONG_DISTANCE && !it.cancelled }
        assumeTrue("no long-distance departure right now", departure != null)
        departure!!

        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        println("${trip.line.name} → ${trip.headsign}: " + trip.stops.joinToString(" – ") { it.name })

        assertTrue("too few stops", trip.stops.size >= 2)
        assertTrue("origin stop not found", indexOfStop(trip.stops, departure.stopId, departure.stopName) >= 0)
        assertTrue("bare final stop '${trip.stops.last().name}'", trip.stops.last().name.lowercase() !in bareNames)
        val times = trip.stops.mapNotNull { it.scheduledDeparture ?: it.scheduledArrival }
        assertTrue("scheduled times go backwards", times.zipWithNext().all { (a, b) -> !b.isBefore(a) })
        println("vehicle: ${vehiclePosition(trip.stops, Instant.now())}")
    }

    @Test
    fun `stops along a trip open as full departure boards`() = runBlocking {
        val stop = repository.searchStops("Frankfurt (Main) Hbf", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.mode == TransportMode.REGIONAL && !it.cancelled }
        assumeTrue("no regional departure right now", departure != null)
        departure!!

        // Trip stops carry platform-level ids; the board must still cover the whole station.
        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        val next = trip.stops.drop(1).firstNotNullOfOrNull { it.place.toStopOrNull() }
        assumeTrue("trip has no further stop with an id", next != null)
        val board = repository.departures(next!!, count = 30).departures
        println("${next.name} (${next.id}): ${board.size} departures, modes ${board.map { it.line.mode }.distinct().sorted()}")
        assertTrue("no departures at ${next.name}", board.isNotEmpty())

        val destination = departure.destination?.toStopOrNull()
        assumeTrue("departure has no destination stop", destination != null)
        println("destination ${destination!!.name} (${destination.id})")
        // The final stop may have no departures left today, but the request has to work.
        repository.departures(destination, count = 5)
        Unit
    }

    @Test
    fun `stop search finds stations by name`() = runBlocking {
        for ((query, expected) in listOf("Alexanderplatz" to "Alexanderplatz", "Marienplatz München" to "Marienplatz", "Köln Dom" to "Dom")) {
            val first = repository.searchStops(query, near = null).firstOrNull()
            println("'$query' → ${first?.name} (${first?.area})")
            assertTrue("'$query' gave ${first?.name}", first != null && first.name.contains(expected))
        }
    }

    @Test
    fun `nearby stops around Cologne cathedral`() = runBlocking {
        val nearby = repository.nearbyStops(LatLon(50.9420, 6.9580))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.any { it.stop.name.contains("Dom") || it.stop.name.contains("Hbf") })
    }
}
