package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.country.Italy
import io.github.dnicotra.ovinfode.country.Netherlands
import io.github.dnicotra.ovinfode.data.api.MatchDto
import io.github.dnicotra.ovinfode.data.api.StopTimesResponse
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
    fun `amsterdam board names trains the Dutch way`() {
        val result = Fixtures.amsterdam
        assertEquals("Amsterdam Centraal", result.stop?.name)
        val departures = result.departures
        assertEquals(34, departures.size)
        assertTrue("no unnamed lines", departures.none { it.line.name == "?" })

        val bus = departures[0]
        assertEquals("42", bus.line.name)
        assertEquals(TransportMode.BUS, bus.line.mode)
        assertEquals("Cruquius-eiland", bus.headsign)
        assertEquals("Amsterdam, Centraal Station", bus.stopName)
        assertEquals("C", bus.platform)
        assertEquals("GVB", bus.operator)
        assertNull("no colour in the feed, none added for buses", bus.line.color)

        val sprinter = departures[1]
        assertEquals("SPR", sprinter.line.name)
        assertEquals(TransportMode.REGIONAL, sprinter.line.mode)
        assertEquals(Netherlands.TRAIN_COLOR, sprinter.line.color)
        assertEquals(Netherlands.TRAIN_TEXT_COLOR, sprinter.line.textColor)
        assertEquals("Uitgeest", sprinter.headsign)
        assertEquals("Amsterdam Centraal", sprinter.stopName)
        assertEquals("5a", sprinter.platform)
        assertEquals("4026", sprinter.tripNumber)
        assertTrue(sprinter.isRealtime)

        assertEquals("IC", departures[4].line.name)
        assertEquals("4b", departures[4].platform)
        assertEquals(6L, departures.first { it.tripNumber == "3928" }.delayMinutes)
        // Metro lines bring their own colours.
        assertEquals(0xFFD81118.toInt(), departures[3].line.color)
        assertEquals(TransportMode.SUBWAY, departures[3].line.mode)

        // A train added in real time, without a route in the feed.
        val extra = departures.single { it.tripId.contains(":IFF:") }
        assertEquals("IC", extra.line.name)
        assertEquals(TransportMode.REGIONAL, extra.line.mode)
        assertEquals("Nijmegen", extra.headsign)
        assertNull(extra.tripNumber)
        assertNull(extra.operator)
    }

    @Test
    fun `utrecht board has no placeholder names`() {
        val departures = Fixtures.utrecht.departures
        assertTrue(departures.none { it.line.name == "?" || it.tripNumber == "?" })
        assertEquals(2, departures.count { it.tripId.contains(":IFF:IC:") && it.line.name == "IC" })
        // IC 3533 to Venlo was cancelled and replaced by an added train at the same time: only that one is shown.
        val venlo = departures.filter { it.headsign == "Venlo" && it.scheduled == Instant.parse("2026-11-26T09:24:00Z") }
        assertEquals(1, venlo.size)
        assertFalse(venlo.single().cancelled)
        assertTrue(venlo.single().tripId.endsWith(":IFF:IC:303533"))
        assertEquals(setOf("IC", "SPR", "S85"), departures.map { it.line.name }.toSet())
        assertEquals("'s-Hertogenbosch", departures.first { it.tripNumber == "8833" }.headsign)
    }

    @Test
    fun `bilingual alerts are shown in one language`() {
        fun alerts(language: String) =
            TransitousApi.json.decodeFromString<StopTimesResponse>(Fixtures.text("stoptimes_amsterdam_leidseplein.json"))
                .toStopDepartures(language).alerts
        val english = alerts("en")
        assertTrue(english.isNotEmpty())
        assertTrue(english.all { " -- " !in it.header })
        // The header is cut off after 140 characters, in the middle of the English part; the description is complete.
        val tram5 = english.single { it.header.startsWith("Stop not served by tram 5.") }
        assertEquals("Stop not served by tram 5. Cause: Damage to the overhead power line. Details: gvb.nl", tram5.header)
        assertEquals(tram5.header, tram5.description)
        assertTrue(alerts("nl").any { it.header == "Tram 5 stopt hier niet. Oorzaak: Schade aan de bovenleiding. Details: gvb.nl" })
    }

    @Test
    fun `dutch trips`() {
        val ic = Fixtures.trip("trip_ic2735.json", "20261126_09:27_nl-OpenOV_395288670")
        assertEquals("IC", ic.line.name)
        assertEquals(Netherlands.TRAIN_COLOR, ic.line.color)
        assertEquals("2735", ic.tripNumber)
        assertEquals("Maastricht", ic.headsign)
        assertEquals(13, ic.stops.size)
        assertEquals("Alkmaar", ic.stops.first().name)
        assertEquals("Utrecht Centraal", ic.stops[6].name)
        assertEquals("18", ic.stops[6].platform)
        assertEquals("'s-Hertogenbosch", ic.stops[7].name)

        val extra = Fixtures.trip("trip_iff_ic3531.json", "20261126_09:12_nl-OpenOV_2026-11-26:IFF:IC:303531")
        assertEquals("IC", extra.line.name)
        assertEquals(TransportMode.REGIONAL, extra.line.mode)
        assertNull(extra.tripNumber)
        assertNull(extra.operator)
        assertEquals("Den Haag Laan v NOI", extra.stops.first().name)
        assertEquals("Venlo", extra.stops.last().name)
    }

    @Test
    fun `roma termini board spells names the Italian way and shows each train once`() {
        val result = Fixtures.romaTermini
        assertEquals("Roma Termini", result.stop?.name)
        val departures = result.departures
        assertTrue(departures.all { it.stopName == "Roma Termini" && it.operator == "Trenitalia" })
        val fr = departures.first { it.line.name == "FR 9490" }
        assertEquals("Venezia S. Lucia", fr.headsign)
        assertEquals(TransportMode.LONG_DISTANCE, fr.line.mode)
        assertEquals(0xFFC8102E.toInt(), fr.line.color)
        assertEquals("9490", fr.tripNumber)
        // Regional trains by category, long-distance trains with their number.
        assertEquals(setOf("RV", "REG"), departures.filter { it.line.mode == TransportMode.REGIONAL }.map { it.line.name }.toSet())
        assertTrue(departures.filter { it.line.mode == TransportMode.LONG_DISTANCE }.all { it.line.name == "FR ${it.tripNumber}" })
        assertTrue(departures.any { it.headsign == "Reggio di Calabria Centrale" })
        assertTrue("headsign in capitals", departures.all { it.headsign.any(Char::isLowerCase) })
        // Trenitalia has REG 4594 and RV 4594 (and FR 8303 twice) at the same time: only the current versions remain.
        val numbers = departures.mapNotNull { it.tripNumber }
        assertEquals("trains shown twice", numbers.size - 1, numbers.toSet().size) // REG 23365 splits into Boiano and Cassino
        assertTrue(departures.single { it.tripNumber == "4594" }.tripId.contains("_0_v_62-4594-"))
        assertEquals("RV", departures.single { it.tripNumber == "4594" }.line.name)
        assertTrue(departures.single { it.tripNumber == "8303" }.tripId.contains("_0_v_"))
        assertEquals(setOf("Boiano", "Cassino"), departures.filter { it.tripNumber == "23365" }.map { it.headsign }.toSet())
    }

    @Test
    fun `milan board has metro lines and names in capitals`() {
        val departures = Fixtures.milanoDuomo.departures
        val metro = departures.filter { it.line.mode == TransportMode.SUBWAY }
        assertTrue(metro.isNotEmpty())
        assertTrue(metro.all { it.line.name == "M3" && it.line.color == Italy.MILAN_METRO_COLORS["M3"] })
        assertEquals(setOf("San Donato", "Comasina"), metro.map { it.headsign }.toSet())
        val trams = departures.filter { it.line.mode == TransportMode.TRAM }
        assertTrue(trams.any { it.headsign == "Rozzano (Via G. Rossa)" })
        assertTrue(trams.any { it.headsign == "P.le Negrelli" && it.stopName == "Duomo M1 M3" })
        assertTrue(departures.all { it.tripNumber == null })
    }

    @Test
    fun `trenord train numbers and turin stop codes`() {
        val cadorna = Fixtures.stopTimes("stoptimes_milano_cadorna.json").departures
        val s3 = cadorna.first { it.line.name == "S3" }
        assertEquals("823", s3.tripNumber)
        assertEquals("Saronno", s3.headsign)
        assertTrue(cadorna.all { it.tripNumber!!.all(Char::isDigit) })

        val turin = Fixtures.stopTimes("stoptimes_torino_porta_nuova.json")
        assertEquals("Porta Nuova", turin.stop?.name)
        assertTrue(turin.departures.any { it.isRealtime })
        assertEquals(
            setOf("Crocetta, Corso Stati Uniti", "Mirafiori Nord, Via Allason (Poste)", "Città Giardino, Via Grosso"),
            turin.departures.map { it.headsign }.toSet(),
        )
    }

    @Test
    fun `rome alerts are cleaned up`() {
        val alerts = Fixtures.stopTimes("stoptimes_roma_termini_bus.json").alerts
        assertTrue(alerts.size >= 5)
        for (alert in alerts) {
            assertFalse(alert.description, "@romamobilita" in alert.description)
            assertFalse(alert.description, '\r' in alert.description || alert.description.any { it.code in 0x80..0x9f })
            assertFalse(alert.description, "&#039;" in alert.description)
        }
        assertTrue(alerts.any { "piazza dell'Esquilino" in it.description })
        assertTrue(alerts.any { "Via dell’Arco di Travertino" in it.description })
    }

    @Test
    fun `italian trip`() {
        val trip = Fixtures.trip("trip_fr9490.json", "20261127_06:10_it-trenitalia_0083_680083_0_v_68-9490-5B67-0083_v_68-9490-5B67-0083")
        assertEquals("FR 9490", trip.line.name)
        assertEquals("9490", trip.tripNumber)
        assertEquals("Trenitalia", trip.operator)
        assertEquals("Venezia S. Lucia", trip.headsign)
        assertEquals(
            listOf("Salerno", "Napoli Centrale", "Roma Termini", "Firenze S. Maria Novella", "Bologna C.le/AV", "Rovigo", "Padova", "Venezia Mestre", "Venezia S. Lucia"),
            trip.stops.map { it.name },
        )
    }

    @Test
    fun `german lines keep their names and colours`() {
        val ic = Fixtures.koeln.departures.first { it.line.name == "IC 2443" }
        assertNull(ic.line.color)
        assertEquals("S3", Fixtures.berlin.departures[0].line.name)
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
