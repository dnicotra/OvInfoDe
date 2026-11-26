package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NetherlandsTest {
    private val trip = "20261126_09:27_nl-OpenOV_395288670"

    private fun line(name: String, mode: TransportMode, color: Int? = null, tripId: String = trip) =
        Netherlands.line(Line(name, mode, color, if (color != null) 0xFFFFFFFF.toInt() else null), tripId)

    @Test
    fun `NS train types get their short names and NS colours`() {
        val ic = line("Intercity", TransportMode.REGIONAL)
        assertEquals("IC", ic.name)
        assertEquals(Netherlands.TRAIN_COLOR, ic.color)
        assertEquals(Netherlands.TRAIN_TEXT_COLOR, ic.textColor)
        assertEquals("SPR", line("Sprinter", TransportMode.REGIONAL).name)
        // Already short, or a line number: kept.
        assertEquals("ICD 1835", line("ICD 1835", TransportMode.LONG_DISTANCE).name)
        assertEquals(Netherlands.TRAIN_COLOR, line("ICD 1835", TransportMode.LONG_DISTANCE).color)
        assertEquals("RS12", line("RS12", TransportMode.REGIONAL).name)
        assertEquals("S85", line("S85", TransportMode.REGIONAL).name)
    }

    @Test
    fun `colours from the feed win, and only trains get NS colours`() {
        assertEquals(0xFF2B267E.toInt(), line("S43", TransportMode.SUBURBAN, color = 0xFF2B267E.toInt()).color)
        assertNull(line("2", TransportMode.TRAM).color)
        assertNull(line("18", TransportMode.BUS).color)
        assertNull(line("52", TransportMode.SUBWAY).color)
    }

    @Test
    fun `rail replacement buses lose the 'instead of train'`() {
        assertEquals("Snelbus", line("Snelbus ipv trein", TransportMode.BUS).name)
        assertEquals("Stopbus", line("Stopbus i.p.v. trein", TransportMode.BUS).name)
        assertEquals("314", line("314", TransportMode.BUS).name)
    }

    @Test
    fun `trains added in real time are named from their trip id`() {
        val extra = line("?", TransportMode.OTHER, tripId = "20261126_09:12_nl-OpenOV_2026-11-26:IFF:IC:303531")
        assertEquals("IC", extra.name)
        assertEquals(TransportMode.REGIONAL, extra.mode)
        assertEquals(Netherlands.TRAIN_COLOR, extra.color)
        assertEquals(TransportMode.LONG_DISTANCE, line("?", TransportMode.OTHER, tripId = "20261126_09:12_nl-OpenOV_2026-11-26:IFF:ICD:1835").mode)
        // Without a train type there is nothing to go by.
        assertEquals("?", line("?", TransportMode.OTHER, tripId = "20261126_09:12_nl-OpenOV_123").name)
    }

    @Test
    fun `stop names`() {
        assertEquals("Den Haag, Centraal Station", Netherlands.stopName("'s-Gravenhage, Centraal Station"))
        assertEquals("Den Haag Centraal", Netherlands.stopName("Den Haag Centraal"))
        assertEquals("'s-Hertogenbosch", Netherlands.stopName("'s-Hertogenbosch"))
        assertEquals("Rotterdam Centraal", Netherlands.stopName("Rotterdam, Rotterdam Centraal"))
        assertEquals("Schiphol Airport/Plaza", Netherlands.stopName("Schiphol, Schiphol Airport/Plaza"))
        assertEquals("Amsterdam, Leidseplein", Netherlands.stopName("Amsterdam, Leidseplein"))
        assertEquals("Amsterdam, Franz Kafkastraat", Netherlands.stopName("Amsterdam, Franz Kafkastraat"))
    }

    @Test
    fun `bilingual alerts show one language`() {
        val gvb = "Tram 5 stopt hier niet. Details: gvb.nl -- Stop not served by tram 5. Details: gvb.nl\n"
        assertEquals("Tram 5 stopt hier niet. Details: gvb.nl", Netherlands.alertText(gvb, "nl"))
        assertEquals("Stop not served by tram 5. Details: gvb.nl", Netherlands.alertText(gvb, "en"))
        assertEquals("Stop not served by tram 5. Details: gvb.nl", Netherlands.alertText(gvb, "de"))
        assertEquals("Geen treinen", Netherlands.alertText("Geen treinen", "en"))
    }

    @Test
    fun `destinations never need a town and trains are just trains`() {
        assertFalse(Netherlands.needsTown(NamedPlace("nl-OpenOV_2993474", "Maastricht", 50.85, 5.70), "nl-OpenOV_2992187"))
        assertEquals(R.string.mode_train, Netherlands.modeLabel(TransportMode.REGIONAL))
        assertNull(Netherlands.modeLabel(TransportMode.SUBWAY))
    }
}
