package io.github.dnicotra.ovinfode.ui.departures

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StopModesTest {
    private val bare = Stop("de-DELFI_de:11000:900100003", "Alexanderplatz", 52.52151, 13.411267)

    @Test
    fun `a stop opened from a trip takes its modes from the departures`() {
        val departures = Fixtures.berlin.departures
        assertEquals(departures.map { it.line.mode }.toSet(), bare.withModesFrom(departures).modes)
    }

    @Test
    fun `known modes and empty boards leave the stop alone`() {
        val known = bare.copy(modes = sortedSetOf(TransportMode.TRAM))
        assertSame(known, known.withModesFrom(Fixtures.berlin.departures))
        assertSame(bare, bare.withModesFrom(emptyList()))
    }
}
