package io.github.dnicotra.ovinfode.ui.departures

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.model.Stop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableOnlyTest {
    private val luxembourg = Stop("fr-horaires-sncf_FR::LMU:c24c10e0-fa0b-11e9-b755-b1228bd14244:", "Luxembourg", 49.5997, 6.1347)
    private val alexanderplatz = Stop("de-DELFI_de:11000:900100003", "Alexanderplatz", 52.52151, 13.411267)

    @Test
    fun `boards in Luxembourg say that their times are from the timetable`() {
        val departures = Fixtures.luxembourg.departures
        assertTrue(DeparturesUiState(luxembourg, departures = departures).timetableOnly)
        // Not before anything is loaded, and not once a train from abroad brings real-time data.
        assertFalse(DeparturesUiState(luxembourg).timetableOnly)
        val live = departures.mapIndexed { i, d -> if (i == 0) d.copy(estimated = d.scheduled) else d }
        assertFalse(DeparturesUiState(luxembourg, departures = live).timetableOnly)
    }

    @Test
    fun `countries with real-time data never do, even when a board has none`() {
        val departures = Fixtures.berlin.departures.map { it.copy(estimated = null) }
        assertFalse(DeparturesUiState(alexanderplatz, departures = departures).timetableOnly)
    }
}
