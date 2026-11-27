package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where departures leave from within a stop (recorded boards, see [Fixtures]). */
class PlatformsTest {

    @Test
    fun `Berlin Alexanderplatz has every U-Bahn platform at its own place`() {
        val places = platformPlaces(Fixtures.berlin.departures)
        val uBahn = places.filter { place -> place.lines.any { it.name.startsWith("U") } }
        assertEquals(6, uBahn.size)
        assertEquals(listOf("1 (U2)"), uBahn.first { it.lines.first().name == "U2" }.platforms.take(1))
        // Platforms are hundreds of metres apart at such a station.
        val u2 = uBahn.first { it.lines.first().name == "U2" }
        val u8 = uBahn.first { it.lines.first().name == "U8" }
        assertTrue(distanceMeters(u2.location, u8.location) > 100)
        // By line: both U2 platforms first.
        assertEquals(listOf("U2", "U2"), uBahn.take(2).map { it.lines.first().name })
    }

    @Test
    fun `Cologne main station, trains at one point and buses at their bays`() {
        val places = platformPlaces(Fixtures.koeln.departures)
        val station = places.first { place -> place.lines.any { it.name.startsWith("ICE") } }
        // DELFI places all tracks of the station at one point: one place with several platforms.
        assertTrue(station.platforms.toString(), station.platforms.size >= 2)
        assertTrue(places.size >= 5)
        // Platforms of one line in natural order: "2" before "10".
        places.groupBy { it.mode to it.lines.first().name }.values.forEach { same ->
            val first = same.mapNotNull { it.platforms.firstOrNull() }
            assertEquals(first.sortedBy { Regex("""^\d+""").find(it)?.value?.toInt() ?: Int.MAX_VALUE }, first)
        }
    }

    @Test
    fun `departures carry their exact place`() {
        val departures = Fixtures.stopTimes("stoptimes_paris_gare_de_lyon_idfm.json").departures
        assertTrue(departures.all { it.stopLocation != null })
        val rer = departures.first { it.line.name == "RER A" }
        assertEquals(48.843575, rer.stopLocation!!.lat, 1e-6)
    }
}
