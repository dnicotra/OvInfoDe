package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanyTest {
    private fun needsTown(name: String, stopId: String?, contextStopId: String?) =
        Germany.needsTown(NamedPlace(stopId, name, 48.78, 9.18), contextStopId)

    @Test
    fun `town is only needed for bare names in another district`() {
        val heidelberg = "de-DELFI_de:08221:1160:5:8"
        assertTrue(needsTown("Hauptbahnhof", "de-DELFI_de:08111:6115:2:3", heidelberg))
        // Seen from Stuttgart itself, "Hauptbahnhof" is clear.
        assertFalse(needsTown("Hauptbahnhof", "de-DELFI_de:08111:6115:2:3", "de-DELFI_de:08111:5006"))
        // "Town, Stop" already names the town.
        assertFalse(needsTown("Mannheim, Hauptbahnhof", "de-DELFI_de:08222:2417:8:9", heidelberg))
        // "Town Hbf" names its town too; a bare "Hbf" does not.
        assertFalse(needsTown("Karlsruhe Hbf", "de-DELFI_de:08212:90:4:5", heidelberg))
        assertFalse(needsTown("Homburg (Saar) Hbf", "de-DELFI_de:10045:8000176", heidelberg))
        assertTrue(needsTown("Hbf", "de-DELFI_de:08111:6115:2:3", heidelberg))
        assertTrue(needsTown("Hbf (tief)", "de-DELFI_de:08111:6115:2:3", heidelberg))
        // Without German ids we cannot tell.
        assertTrue(needsTown("Messe", "be-sncb_8015345", heidelberg))
        assertTrue(needsTown("Messe", "de-DELFI_de:08111:6115:2:3", "nl-OpenOV_2992187"))
    }

    @Test
    fun `other countries only skip names that already have a town`() {
        val messe = NamedPlace("be-sncb_8015345", "Messe", 50.77, 6.09)
        assertTrue(OtherCountry.needsTown(messe, null))
        assertFalse(OtherCountry.needsTown(messe.copy(name = "Aachen, Messe"), null))
        assertFalse(OtherCountry.needsTown(messe.copy(name = "Salzburg Hbf"), null))
    }

    @Test
    fun `U-Bahn and S-Bahn are German names`() {
        assertEquals(R.string.mode_subway_de, Germany.modeLabel(TransportMode.SUBWAY))
        assertEquals(R.string.mode_suburban_de, Germany.modeLabel(TransportMode.SUBURBAN))
        assertNull(Germany.modeLabel(TransportMode.REGIONAL))
        assertNull(OtherCountry.modeLabel(TransportMode.SUBWAY))
    }
}
