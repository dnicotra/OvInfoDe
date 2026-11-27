package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.FakeTransitous
import io.github.dnicotra.ovinfode.data.TransitRepository
import io.github.dnicotra.ovinfode.data.model.Stop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stops of one place that the timetables keep apart (map stops recorded on 2026-11-27, 400 m around each stop). */
class SimilarStopsTest {

    @Test
    fun `similar names`() {
        assertTrue(similarStopNames("Gare de Lyon", "Paris Gare de Lyon"))
        assertTrue(similarStopNames("Lyon Part Dieu", "Gare Part-Dieu V.Merle"))
        assertTrue(similarStopNames("Köln Hbf", "Köln Dom/Hbf"))
        assertTrue(similarStopNames("S+U Alexanderplatz Bhf (Berlin)", "S+U Alexanderplatz Bhf/Dircksenstr. (Berlin)"))
        assertTrue(similarStopNames("Amsterdam Centraal", "Amsterdam, Centraal Station"))
        assertFalse(similarStopNames("Köln Hbf", "Köln Neumarkt"))
        assertFalse(similarStopNames("Memhardstr. (Berlin)", "S+U Alexanderplatz Bhf (Berlin)"))
        assertFalse("only the word 'station' in common", similarStopNames("Gare de Lyon", "Gare du Nord"))
        assertFalse(similarStopNames("Hauptbahnhof", "Bahnhof"))
        assertFalse(similarStopNames("", "Gare de Lyon"))
    }

    private fun similar(fixture: String, stop: Stop): List<String> = runBlocking {
        val fake = FakeTransitous(mapOf("/api/v6/map/stops" to fixture))
        TransitRepository(fake.api, userLanguage = { "fr" }).similarStops(stop).map { it.stop.name }
    }

    @Test
    fun `Paris Gare de Lyon, the metro and RER stop and the SNCF station`() {
        val idfm = Stop("fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:73626", "Gare de Lyon", 48.84457, 2.3751457)
        val fromMetro = similar("mapstops_similar_paris_gare_de_lyon.json", idfm)
        assertEquals(listOf("Paris Gare de Lyon"), fromMetro)
        val sncf = Stop("fr-horaires-sncf_FR::LMU:6451bcf0-cb0a-11e8-8bfa-f784c1c7c611:", "Paris Gare de Lyon", 48.844945, 2.373481)
        assertEquals(listOf("Gare de Lyon"), similar("mapstops_similar_paris_gare_de_lyon.json", sncf))
    }

    @Test
    fun `Lyon Part-Dieu, the station and the TCL stops around it`() {
        val sncf = Stop("fr-horaires-sncf_FR::LMU:6af3bc70-cb0a-11e8-8bfa-f784c1c7c611:", "Lyon Part Dieu", 45.760596, 4.859409)
        val names = similar("mapstops_similar_lyon_part_dieu.json", sncf)
        assertTrue(names.toString(), names.containsAll(listOf("Gare Part-Dieu V.Merle", "Gare Part-Dieu Villette", "Lyon-Part-Dieu Gare Routière")))
        assertFalse(names.toString(), "Georges Pompidou" in names)
        assertFalse("the AVE copy of the station itself", names.any { it == "Lyon Part Dieu" })
    }

    @Test
    fun `Berlin Alexanderplatz, the station and its bus and tram stops`() {
        val station = Stop("de-DELFI_de:11000:900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.52151, 13.411267)
        val names = similar("mapstops_similar_berlin_alexanderplatz.json", station.copy(name = TransitRepositoryNames.name(station)))
        assertTrue(names.toString(), names.size >= 3)
        assertTrue(names.toString(), names.all { "Alexanderplatz" in it })
    }
}

/** The stop's name as the app shows it, for building stops by hand. */
private object TransitRepositoryNames {
    fun name(stop: Stop): String = io.github.dnicotra.ovinfode.country.Countries.of(stop.id, stop.lat, stop.lon).stopName(stop.name)
}
