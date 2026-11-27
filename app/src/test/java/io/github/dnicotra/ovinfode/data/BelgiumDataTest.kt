package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.FakeTransitous
import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.country.Belgium
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Belgian boards and trips (recorded responses, see [Fixtures]) and the languages they are asked for in. */
class BelgiumDataTest {
    private val bruxellesCentral = Stop("be-sncb_S8813003", "Bruxelles-Central", 50.845658, 4.356801)
    private val antwerpenCentraal = Stop("be-sncb_S8821006", "Antwerpen-Centraal", 51.2172, 4.4211)
    private val liege = Stop("be-sncb_S8841004", "Liège-Guillemins", 50.6246, 5.5667)
    private val icTrip = "20261127_07:06_be-sncb_88____:007::8831765:8892007:19:947:20261211"

    @Test
    fun `SNCB board in Brussels`() {
        val result = Fixtures.stopTimes("stoptimes_bruxelles_central.json")
        assertEquals("Bruxelles-Central", result.stop?.name)
        val departures = result.departures
        assertEquals(29, departures.size)

        val ic = departures[0]
        assertEquals("IC", ic.line.name)
        assertEquals(TransportMode.REGIONAL, ic.line.mode)
        assertEquals("2331", ic.tripNumber)
        assertEquals("Ostende", ic.headsign)
        assertEquals("NMBS/SNCB", ic.operator)

        val s2 = departures[1]
        assertEquals("S2", s2.line.name)
        assertEquals(TransportMode.SUBURBAN, s2.line.mode)
        assertEquals(0xFFF26222.toInt(), s2.line.color)

        val ec = departures.single { it.line.name == "EC" }
        assertEquals(TransportMode.LONG_DISTANCE, ec.line.mode)
        assertEquals("Rotterdam Centraal (NL)", ec.headsign)
        assertEquals("9223", ec.tripNumber)

        assertEquals(setOf("IC", "P", "EC", "S1", "S2", "S3", "S8", "S10"), departures.map { it.line.name }.toSet())
        // French names, as asked: no "Antwerpen-Centraal / Anvers-Central".
        assertTrue(departures.none { it.headsign.contains(" / ") })
    }

    @Test
    fun `SNCB board in Antwerp, in Dutch`() {
        val departures = Fixtures.stopTimes("stoptimes_antwerpen_centraal.json").departures
        assertEquals(listOf("Leuven", "Poperinge", "Nijvel"), departures.take(3).map { it.headsign })
        assertEquals("L", departures.first { it.tripNumber == "2858" }.line.name)
        assertEquals("Roosendaal (NL)", departures.first { it.line.name == "S32" && it.tripNumber == "2558" }.headsign)
    }

    @Test
    fun `STIB destinations are written normally`() {
        val result = Fixtures.stopTimes("stoptimes_bruxelles_de_brouckere.json")
        assertEquals("De Brouckère", result.stop?.name)
        val departures = result.departures
        assertEquals("Gare de l'Ouest", departures[0].headsign)
        assertEquals(TransportMode.SUBWAY, departures[0].line.mode)
        assertEquals("1", departures[0].line.name)
        assertEquals("Hôpital Militaire", departures[2].headsign) // "HOP. MILITAIRE"
        // Line 71 also ends here: "DE BROUCKERE" had lost its accent.
        assertTrue(departures.any { it.line.name == "71" && it.headsign == "De Brouckère" })
        assertTrue("no capitals", departures.none { d -> d.headsign.none { it.isLowerCase() } && d.headsign != "UZ-VUB" })
        assertEquals("STIB", departures[0].operator)
        assertNull("STIB trips have no numbers", departures[0].tripNumber)
    }

    @Test
    fun `TEC and SNCB at Liège-Guillemins`() {
        val result = Fixtures.stopTimes("stoptimes_liege_guillemins.json")
        assertEquals("Liège-Guillemins", result.stop?.name)
        val departures = result.departures

        val bus = departures[0]
        assertEquals("B2", bus.line.name)
        assertEquals("Sart-Tilman, Botanique", bus.headsign)
        assertEquals("Liège, Gare des Guillemins", bus.stopName)
        assertEquals("B", bus.platform)
        assertEquals("TEC", bus.operator)
        assertNull(bus.tripNumber)
        assertEquals("Liège Expo", departures[1].headsign) // "LIEGE Liège Expo"
        assertEquals("Visé, Place des Déportés", departures.first { it.line.name == "61" && it.headsign.startsWith("V") }.headsign)

        val train = departures.first { it.line.mode == TransportMode.REGIONAL }
        assertEquals("IC", train.line.name)
        assertEquals("Welkenraedt", train.headsign)
        assertEquals("Liège-Guillemins", train.stopName)
        assertTrue(departures.none { it.headsign.startsWith("LIEGE") })
    }

    @Test
    fun `De Lijn stops without their platform in the name`() {
        val result = Fixtures.stopTimes("stoptimes_antwerpen_centraal_station_delijn.json")
        assertEquals("Antwerpen Centraal Station", result.stop?.name)
        val bus = result.departures[1]
        assertEquals("21", bus.line.name)
        assertEquals("Antwerpen Centraal Station", bus.stopName)
        assertEquals("11", bus.platform)
        assertEquals("De Lijn", bus.operator)
        assertNull(bus.tripNumber)
        assertTrue(result.departures.none { it.stopName.contains("perron") || it.destination?.name?.contains("perron") == true })
    }

    @Test
    fun `trips`() {
        val ic = Fixtures.trip("trip_be_ic.json", icTrip, language = "fr")
        assertEquals("IC", ic.line.name)
        assertEquals("3629", ic.tripNumber)
        assertEquals("Gand-Saint-Pierre", ic.headsign)
        assertEquals(19, ic.stops.size)
        assertEquals(listOf("Genk", "Bokrijk", "Kiewit", "Hasselt"), ic.stops.take(4).map { it.name })
        assertTrue(ic.stops.none { it.name.contains(" / ") })

        val bus = Fixtures.trip("trip_tec_b2.json", "20261127_08:30_be-tec_gt:tec:55536811-L_PA_2026-26_LG_N3-Sem-N-3-11", language = "fr")
        assertEquals("TEC", bus.operator)
        assertNull(bus.tripNumber)
        assertEquals("Sart-Tilman, Botanique", bus.headsign)
        assertEquals("Liège, Gare des Guillemins - Quai B", bus.stops[0].name)
        assertEquals("Angleur, Pont du Val Benoît", bus.stops[3].name)
    }

    @Test
    fun `German and English readers get the names of the signs, Dutch and French readers their own`() = runBlocking {
        val fake = FakeTransitous(
            mapOf(
                "/api/v6/stoptimes" to "stoptimes_bruxelles_central.json",
                "/api/v6/trip" to "trip_be_ic.json",
                "/api/v6/map/stops" to "mapstops_bruxelles_centre.json",
                "/api/v1/geocode" to "geocode_alexanderplatz.json",
            ),
        )
        fun languages(path: String) = fake.requestsTo(path).map { it.url.queryParameter("language") }

        val german = TransitRepository(fake.api, userLanguage = { "de" })
        german.departures(bruxellesCentral, count = 30)
        german.departures(antwerpenCentraal, count = 30)
        german.departures(liege, count = 30)
        assertEquals(listOf("fr", "nl", "fr"), languages("/api/v6/stoptimes"))

        // A trip opened from the Antwerp board is asked for in Dutch, one without a board in French.
        german.trip(icTrip, contextStopId = antwerpenCentraal.id)
        german.trip(icTrip, contextStopId = null)
        assertEquals(listOf("nl", "fr"), languages("/api/v6/trip"))

        german.nearbyStops(LatLon(51.0543, 3.7174)) // Gent
        assertEquals("nl", languages("/api/v6/map/stops").last())

        // Search answers with the names that matched, in any language.
        german.searchStops("Brüssel", near = null)
        assertEquals(listOf("de"), languages("/api/v1/geocode"))

        val dutch = TransitRepository(fake.api, userLanguage = { "nl" })
        dutch.departures(liege, count = 30)
        assertEquals("nl", languages("/api/v6/stoptimes").last())
        val french = TransitRepository(fake.api, userLanguage = { "fr" })
        french.departures(antwerpenCentraal, count = 30)
        assertEquals("fr", languages("/api/v6/stoptimes").last())

        // Elsewhere nothing changes.
        german.departures(Stop("de-DELFI_de:11000:900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.52151, 13.411267), count = 30)
        assertEquals("de", languages("/api/v6/stoptimes").last())
        assertEquals(0, fake.requestsTo("/api/v1/reverse-geocode").size)
    }

    @Test
    fun `nearby stops in Brussels`() = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/map/stops" to "mapstops_bruxelles_centre.json"))
        val nearby = TransitRepository(fake.api, userLanguage = { "en" }).nearbyStops(LatLon(50.8455, 4.3570))
        assertEquals("fr", fake.requestsTo("/api/v6/map/stops").first().url.queryParameter("language"))
        assertEquals("Bruxelles-Central", nearby.first().stop.name)
        assertTrue("found in the first request", nearby.size >= 5)
    }

    @Test
    fun `TEC names stay the same when shown again`() {
        // Favourites are stored with their shown name and named again when loaded.
        val names = Fixtures.belgian.filter { "liege" in it || "tec" in it }
            .flatMap { file -> Regex(""""(?:name|headsign)"\s*:\s*"([^"]+)"""").findAll(Fixtures.text(file)).map { it.groupValues[1] } }
            .toSet()
        assertTrue("names found: ${names.size}", names.size > 20)
        for (name in names) {
            val shown = Belgium.stopName(name, stopId = "be-tec_gs:tec:x")
            assertEquals("not idempotent: $name", shown, Belgium.stopName(shown, stopId = "be-tec_gs:tec:x"))
        }
    }
}
