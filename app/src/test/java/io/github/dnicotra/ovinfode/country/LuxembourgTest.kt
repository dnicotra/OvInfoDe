package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxembourgTest {

    @Test
    fun `stop ids of the national feed say the country`() {
        assertEquals("lu", Luxembourg.countryInStopId("000200405020")) // Hamilius
        assertEquals("lu", Luxembourg.countryInStopId("000140701022")) // Ettelbruck, Gare
        assertEquals("lu", Luxembourg.countryInStopId("000900000123")) // provisional stop
        assertEquals("be", Luxembourg.countryInStopId("000300000012")) // Athus
        assertEquals("fr", Luxembourg.countryInStopId("000400000034")) // Volmerange-les-Mines
        assertEquals("de", Luxembourg.countryInStopId("000500000079")) // Trier, Hauptbahnhof
        assertNull(Luxembourg.countryInStopId("12345"))
        assertNull(Luxembourg.countryInStopId("000600000001"))
    }

    @Test
    fun `destinations never need a town and trains are just trains`() {
        assertFalse(Luxembourg.needsTown(NamedPlace("lu-administration-des-transports-publics_000200405020", "Hamilius (Bus)", 49.6115, 6.126), null))
        assertEquals(R.string.mode_train, Luxembourg.modeLabel(TransportMode.REGIONAL))
        assertNull(Luxembourg.modeLabel(TransportMode.TRAM))
        assertNull(Luxembourg.modeLabel(TransportMode.LONG_DISTANCE))
    }

    @Test
    fun `no real-time data in Luxembourg`() {
        assertFalse(Luxembourg.hasRealtimeData)
        assertTrue(Germany.hasRealtimeData)
        assertTrue(Netherlands.hasRealtimeData)
    }

    @Test
    fun `stop and line names stay as in the national timetable`() {
        assertEquals("Esch-sur-Alzette, Gare", Luxembourg.stopName("Esch-sur-Alzette, Gare"))
        assertEquals("Bonnevoie, Lycée Bouneweg (Tram)", Luxembourg.stopName("Bonnevoie, Lycée Bouneweg (Tram)"))
        // Luxembourgish and French names are no German abbreviations.
        assertEquals("Strassen, Primeurs", Luxembourg.stopName("Strassen, Primeurs"))
        assertEquals("Cents, Waassertuerm", Luxembourg.stopName("Cents, Waassertuerm"))
        assertEquals("Hesperange, Cité Um Schlass", Luxembourg.stopName("Hesperange, Cité Um Schlass"))
        // German stations reached from Luxembourg get the German short forms.
        assertEquals("Trier Hbf", Countries.of("lu-administration-des-transports-publics_000500000079", 49.757, 6.651).stopName("Trier, Hauptbahnhof"))
        val re = Line("RE", TransportMode.REGIONAL)
        assertEquals(re, Luxembourg.line(re, "20261127_09:33_lu-administration-des-transports-publics_24658350"))
    }
}
