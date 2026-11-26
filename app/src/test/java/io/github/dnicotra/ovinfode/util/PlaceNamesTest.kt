package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.data.model.NamedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {

    @Test
    fun `district key is read from German stop ids of any feed`() {
        assertEquals("08111", districtOf("de-DELFI_de:08111:6115:2:3"))
        assertEquals("08221", districtOf("at-Railway-Current-Reference-Data-2026_de:08221:1160:1:1"))
        assertEquals("11000", districtOf("de-VBB_de:11000:900100003:2:53"))
        assertEquals("06412", districtOf("de-DELFI_de:06412:10_G"))
        assertNull(districtOf("be-sncb_8015345"))
        assertNull(districtOf(null))
    }

    @Test
    fun `platforms of one station share a key`() {
        fun key(id: String?) = placeKey(NamedPlace(id, "x", 48.7855, 9.1824))
        assertEquals("de:08111:6115", key("de-DELFI_de:08111:6115:2:3"))
        assertEquals(key("de-DELFI_de:08111:6115:2:3"), key("de-DELFI_de:08111:6115:4:7"))
        assertEquals("de:06412:10", key("de-DELFI_de:06412:10_G"))
        assertEquals("be-sncb_8015345", key("be-sncb_8015345"))
        assertEquals("48.7855,9.1824", key(null))
    }

    @Test
    fun `town is only needed for bare names in another district`() {
        val heidelberg = "de-DELFI_de:08221:1160:5:8"
        assertTrue(needsTown("Hauptbahnhof", "de-DELFI_de:08111:6115:2:3", heidelberg))
        // Seen from Stuttgart itself, "Hauptbahnhof" is clear.
        assertFalse(needsTown("Hauptbahnhof", "de-DELFI_de:08111:6115:2:3", "de-DELFI_de:08111:5006"))
        // "Town, Stop" already names the town.
        assertFalse(needsTown("Mannheim, Hauptbahnhof", "de-DELFI_de:08222:2417:8:9", heidelberg))
        // Without German ids we cannot tell.
        assertTrue(needsTown("AACHEN Hauptbahnhof", "be-sncb_8015345", heidelberg))
    }

    @Test
    fun `names that already mention the town are kept`() {
        assertEquals("Stuttgart, Hauptbahnhof", qualifyName("Hauptbahnhof", "Stuttgart"))
        assertEquals("Frankfurt (Main) Hauptbahnhof", qualifyName("Frankfurt (Main) Hauptbahnhof", "Frankfurt am Main"))
        assertEquals("Homburg (Saar) Hbf", qualifyName("Homburg (Saar) Hbf", "Homburg"))
        assertEquals("Wiesloch-Walldorf, Bf", qualifyName("Wiesloch-Walldorf, Bf", "Walldorf"))
        assertEquals("Karlsruhe Hauptbahnhof", qualifyName("Karlsruhe Hauptbahnhof", "Karlsruhe"))
        assertEquals("Ulm, Hbf", qualifyName("Hbf", "Ulm"))
        assertEquals("Hauptbahnhof", qualifyName("Hauptbahnhof", null))
        assertEquals("Hauptbahnhof", qualifyName("Hauptbahnhof", ""))
    }
}
