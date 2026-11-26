package io.github.dnicotra.ovinfode.ui.search

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.api.MatchDto
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.toStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    // Recorded results for "Alexanderplatz": Berlin, Berlin (Flixbus), Chur, Idar-Oberstein, Krefeld.
    private val stops = TransitousApi.json.decodeFromString<List<MatchDto>>(Fixtures.text("geocode_alexanderplatz.json")).map { it.toStop() }

    @Test
    fun `nearest result comes first when the location is known`() {
        val inKrefeld = LatLon(51.333, 6.563)
        val ranked = rankByDistance(stops, inKrefeld)
        assertEquals("Krefeld Alexanderplatz", ranked.first().stop.name)
        val distances = ranked.map { it.distanceMeters!! }
        assertEquals(distances.sorted(), distances)
        assertTrue(distances.first() < 2_000)
    }

    @Test
    fun `api order is kept without a location`() {
        val ranked = rankByDistance(stops, null)
        assertEquals(stops.map { it.id }, ranked.map { it.stop.id })
        assertTrue(ranked.all { it.distanceMeters == null })
    }

    @Test
    fun `empty results stay empty`() {
        assertTrue(rankByDistance(emptyList(), LatLon(52.52, 13.40)).isEmpty())
        assertNull(rankByDistance(emptyList(), null).firstOrNull())
    }
}
