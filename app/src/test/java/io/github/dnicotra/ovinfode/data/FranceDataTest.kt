package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.api.PlaceDto
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** French boards and trips (recorded responses, see [Fixtures]). */
class FranceDataTest {

    @Test
    fun `Paris Gare de Lyon, the Île-de-France stop`() {
        val result = Fixtures.stopTimes("stoptimes_paris_gare_de_lyon_idfm.json")
        assertEquals("Gare de Lyon", result.stop?.name)
        val departures = result.departures
        val lines = departures.map { it.line.name }.toSet()
        assertTrue(lines.toString(), lines.containsAll(listOf("RER A", "RER D", "M1", "M14")))
        val rer = departures.filter { it.line.name == "RER A" }
        assertTrue(rer.all { it.line.mode == TransportMode.SUBURBAN })
        // Mission codes ("UZAR") give way to the final stop, and become the trip number.
        assertTrue(rer.map { it.headsign }.toString(), rer.none { it.headsign.matches(Regex("[A-Z]{4}")) })
        assertTrue(rer.all { it.tripNumber?.matches(Regex("[A-Z]{4}")) == true })
        val metro = departures.first { it.line.name == "M1" }
        assertEquals(TransportMode.SUBWAY, metro.line.mode)
        assertNull("the metro's \"RATP\" is no trip number", metro.tripNumber)
        assertTrue(departures.filter { it.line.mode == TransportMode.BUS }.all { it.tripNumber == null })
        assertTrue(departures.any { it.isRealtime })
        // Alerts come as HTML.
        assertTrue(result.alerts.isNotEmpty())
        assertTrue(result.alerts.none { "<p>" in it.description || "<br>" in it.description || "&#" in it.description })
    }

    @Test
    fun `SNCF trains at Lyon Part-Dieu`() {
        val departures = Fixtures.stopTimes("stoptimes_lyon_part_dieu.json").departures
        val tgv = departures.first { it.line.name == "TGV" }
        assertEquals(TransportMode.LONG_DISTANCE, tgv.line.mode)
        assertEquals("SNCF", tgv.operator)
        assertTrue(tgv.tripNumber!!.all(Char::isDigit))
        val ter = departures.first { it.line.name == "TER" }
        assertEquals(TransportMode.REGIONAL, ter.line.mode)
        // SNCF sends no headsigns: the final stop, without the hall of Paris Gare de Lyon.
        assertTrue(departures.none { it.headsign.isBlank() || "Hall" in it.headsign })
        assertTrue(departures.any { it.headsign == "Paris Gare de Lyon" })
        assertTrue(departures.none { it.line.name.matches(Regex(""".* \d{5,6}""")) })
        // The Frecciarossa to Paris once, with the French real-time data and the Italian number.
        val frecce = departures.filter { it.line.name.startsWith("FR") && it.scheduled == departures.first { d -> d.line.name.startsWith("FR") }.scheduled }
        assertEquals(1, frecce.size)
        assertEquals("FR 6648", frecce.single().line.name)
        assertEquals("6648", frecce.single().tripNumber)
        assertTrue(frecce.single().isRealtime)
        assertEquals("Paris Gare de Lyon", frecce.single().headsign)
    }

    @Test
    fun `Strasbourg, a French station in the German feed`() {
        val result = Fixtures.stopTimes("stoptimes_strasbourg.json")
        assertEquals("Strasbourg", result.stop?.name)
        assertTrue(result.departures.all { it.line.name in setOf("TER", "TGV", "OUIGO", "IC", "Lyria") || it.line.mode == TransportMode.BUS })
    }

    @Test
    fun `Paris Nord`() {
        val departures = Fixtures.stopTimes("stoptimes_paris_nord_sncf.json").departures
        assertTrue(departures.none { it.headsign.isBlank() })
        assertTrue(departures.none { it.line.name.length > 8 })
    }

    @Test
    fun `Mulhouse names in capitals`() {
        val places = TransitousApi.json.decodeFromString<List<PlaceDto>>(Fixtures.text("mapstops_mulhouse.json"))
        val names = places.map { it.toStop().name }
        assertTrue(names.toString(), "Mulhouse, Zoo" in names)
        assertFalse(names.toString(), names.any { it.startsWith("MULHOUSE - ") })
        val shouting = names.filter { name -> name.none { it.isLowerCase() } && name.count { it.isLetter() } > 4 }
        assertTrue(shouting.toString(), shouting.isEmpty())
    }

    @Test
    fun `trips`() {
        val rer = Fixtures.trip("trip_rer_a.json", "20261127_05:33_fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:TN:SNCF:5c4b9a19-560d-4ec8-92fb-ba5a5b4e3857", "fr")
        assertEquals("RER A", rer.line.name)
        assertEquals("Cergy le Haut", rer.headsign)
        assertEquals("UZAR", rer.tripNumber)
        val tgv = Fixtures.trip("trip_tgv6602.json", "20261127_05:50_fr-horaires-sncf_FR:ServiceJourney::SN6602FERRE_13497088", "fr")
        assertEquals("TGV", tgv.line.name)
        assertEquals("6602", tgv.tripNumber)
        assertTrue(tgv.headsign.isNotBlank())
        assertTrue(tgv.stops.none { "Hall" in it.name })
    }
}
