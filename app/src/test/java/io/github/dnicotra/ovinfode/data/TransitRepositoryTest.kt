package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.FakeTransitous
import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.api.ApiException
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Repository tests against recorded responses (no network). */
class TransitRepositoryTest {

    private val heidelberg = Stop("at-Railway-Current-Reference-Data-2026_de:08221:1160:1:1", "Heidelberg Hbf", 49.4035, 8.6748)

    @Test
    fun `departures send stop id with coordinate fallback and a user agent`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_berlin_alexanderplatz.json"))
        val stop = Stop("de-DELFI_de:11000:900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.52151, 13.411267)

        val result = TransitRepository(fake.api).departures(stop, count = 30)

        assertEquals(Fixtures.berlin.departures.size, result.departures.size)
        val request = fake.requestsTo("/api/v6/stoptimes").single()
        assertEquals("de-DELFI_de:11000:900100003", request.url.queryParameter("stopId"))
        assertEquals("52.521510,13.411267", request.url.queryParameter("center"))
        assertEquals("30", request.url.queryParameter("radius"))
        assertEquals("30", request.url.queryParameter("n"))
        assertTrue(fake.requests.all { it.header("User-Agent") == "Abfahrten/test (+https://example.org)" })
    }

    @Test
    fun `bare destination names get their town, everything else is left alone`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_heidelberg_hbf.json"))
        val repository = TransitRepository(fake.api)

        val departures = repository.departures(heidelberg, count = 10).departures
        fun headsignOf(line: String) = departures.first { it.line.name == line }.headsign

        // DELFI calls Stuttgart Hbf just "Hauptbahnhof"; the app shows "Stuttgart Hbf".
        assertEquals("Stuttgart Hbf", headsignOf("ICE 1697"))
        assertEquals("Karlsruhe Hbf", headsignOf("RE73"))
        assertEquals("Frankfurt (Main) Hbf", headsignOf("RB68"))
        assertEquals("Homburg (Saar) Hbf", departures.first { it.headsign.startsWith("Homburg") }.headsign)
        assertEquals("Mannheim Hbf", headsignOf("RE10a"))
        // Written by the operator, not the name of the last stop: untouched.
        assertEquals("Trier Hbf", headsignOf("RE1"))

        // Only "Eppingen" and the bare "Hbf" of Stuttgart need a lookup; "Karlsruhe Hbf",
        // "Mannheim Hbf" etc. already name their town.
        val lookups = fake.requestsTo("/api/v1/reverse-geocode").size
        assertEquals(2, lookups)

        repository.departures(heidelberg, count = 10)
        assertEquals("towns are cached", lookups, fake.requestsTo("/api/v1/reverse-geocode").size)
    }

    @Test
    fun `destinations open as stops under the name the board shows`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_heidelberg_hbf.json"))

        val ice = TransitRepository(fake.api).departures(heidelberg, count = 10).departures.first { it.line.name == "ICE 1697" }
        val stop = ice.destination?.toStopOrNull()

        assertEquals("Stuttgart Hbf", stop?.name)
        assertTrue("destination has a stop id", !stop?.id.isNullOrBlank())
    }

    @Test
    fun `every stop of a trip opens as a departure board`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/trip" to "trip_ice1697.json"))

        val trip = TransitRepository(fake.api).trip("20261126_07:03_de-DELFI_3490184955", contextStopId = "de-DELFI_de:08221:1160:5:8")
        val stops = trip.stops.map { it.place.toStopOrNull() }

        assertTrue("stop without id", stops.all { it != null })
        assertEquals(trip.stops.map { it.name }, stops.map { it!!.name })
        assertEquals("Heidelberg Hbf", stops[2]!!.name)
    }

    @Test
    fun `trip names its final stop with the town`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/trip" to "trip_ice1697.json"))

        val trip = TransitRepository(fake.api).trip("20261126_07:03_de-DELFI_3490184955", contextStopId = "de-DELFI_de:08221:1160:5:8")

        assertEquals("Stuttgart Hbf", trip.headsign)
        assertEquals("Stuttgart Hbf", trip.stops.last().name)
        assertEquals("Frankfurt (Main) Hbf", trip.stops.first().name)
        assertEquals("Heidelberg Hbf", trip.stops[2].name)
        // Only the bare "Hbf" is looked up.
        assertEquals(1, fake.requestsTo("/api/v1/reverse-geocode").size)
    }

    @Test
    fun `nearby stops are sorted by distance and the radius grows until enough are found`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/map/stops" to "mapstops_koeln.json"))
        val nearby = TransitRepository(fake.api).nearbyStops(LatLon(50.9420, 6.9580))

        assertEquals("Köln Dom/Hbf", nearby.first().stop.name)
        assertEquals(nearby.map { it.distanceMeters }.sorted(), nearby.map { it.distanceMeters })
        assertEquals(6, nearby.size)
        // 4 stops within 500 m is not enough, so a second request with 1000 m is made.
        assertEquals(2, fake.requestsTo("/api/v6/map/stops").size)
    }

    @Test
    fun `dutch boards and trips need no town lookups`() = runBlocking {
        val fake = FakeTransitous(
            mapOf(
                "/api/v6/stoptimes" to "stoptimes_amsterdam_centraal.json",
                "/api/v6/trip" to "trip_ic2735.json",
            ),
        )
        val repository = TransitRepository(fake.api)
        val amsterdam = Stop("de-DELFI_000008400058", "Amsterdam Centraal", 52.37919, 4.899431)

        val departures = repository.departures(amsterdam, count = 30).departures
        assertEquals("Maastricht", repository.trip("20261126_09:27_nl-OpenOV_395288670", contextStopId = "nl-OpenOV_2992187").headsign)

        assertEquals(34, departures.size)
        // Before, every bare destination ("Uitgeest", "Heerlen") cost a lookup and up to 2.5 s.
        assertEquals(0, fake.requestsTo("/api/v1/reverse-geocode").size)
        assertEquals(listOf("Uitgeest", "Hoofddorp"), departures.drop(1).take(2).map { it.headsign })
    }

    @Test
    fun `nearby stops in Amsterdam`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/map/stops" to "mapstops_amsterdam_dam.json"))
        val nearby = TransitRepository(fake.api).nearbyStops(LatLon(52.37310, 4.89260))

        assertEquals("Amsterdam, Dam", nearby.first().stop.name)
        assertEquals(nearby.map { it.distanceMeters }.sorted(), nearby.map { it.distanceMeters })
        assertEquals(4, nearby.size)
    }

    @Test
    fun `server errors become ApiException and map to SERVER`() = runBlocking {
        val fake = FakeTransitous(override = { 500 to """{"error":"internal"}""" })
        try {
            TransitRepository(fake.api).trip("x")
            fail("expected an exception")
        } catch (e: ApiException) {
            assertEquals(500, e.code)
            assertEquals("internal", e.message)
            assertEquals(ErrorKind.SERVER, e.toErrorKind())
        }
    }

    @Test
    fun `error kinds`() {
        assertEquals(ErrorKind.NETWORK, IOException("offline").toErrorKind())
        assertEquals(ErrorKind.NOT_FOUND, ApiException(404, null).toErrorKind())
        assertTrue(runBlocking { safeCall<Unit> { throw IOException("x") }.isFailure })
    }
}
