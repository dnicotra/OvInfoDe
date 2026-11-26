package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Trip ids and names as Transitous returned them on 2026-11-27. */
class ItalyTest {

    private fun line(name: String, mode: TransportMode, tripId: String, color: Int? = null) =
        Italy.line(Line(name, mode, color, if (color != null) 0xFF000000.toInt() else null), tripId)

    @Test
    fun `Milan's metro lines are M1 to M5 in the colours of the signs`() {
        val atm = "20261127_08:14_it-Lombardia-ATM_COMASINA___SAN DONATO_l_20260907_273"
        val m3 = line("3", TransportMode.SUBWAY, atm, color = 0xFFFCFF01.toInt())
        assertEquals("M3", m3.name)
        assertEquals(Italy.MILAN_METRO_COLORS["M3"], m3.color)
        assertEquals(0xFF000000.toInt(), m3.textColor)
        val m1 = line("1", TransportMode.SUBWAY, atm, color = 0xFFFF0000.toInt())
        assertEquals("M1", m1.name)
        assertEquals(0xFFEE2E26.toInt(), m1.color)
        assertEquals(0xFFFFFFFF.toInt(), m1.textColor)
        // Trams and buses with the same numbers, and other cities' metros, stay as they are.
        assertEquals("3", line("3", TransportMode.TRAM, "20261127_08:31_it-Lombardia-ATM_20261026_INV LV BASE").name)
        assertEquals("M1", line("M1", TransportMode.SUBWAY, "20261127_08:30_it-Piemonte-Gruppo-Torinese-Trasporti_1").name)
        assertEquals("L2", line("L2", TransportMode.SUBWAY, "20261127_08:30_it-Campania-ANM_1").name)
    }

    @Test
    fun `Rome's metro lines are A, B, B1 and C`() {
        val rome = "20261127_08:12_it-Lazio-Rome_50818_1000000101995_2"
        assertEquals("A", line("MEA", TransportMode.SUBWAY, rome, color = 0xFFE27439.toInt()).name)
        assertEquals(0xFFE27439.toInt(), line("MEA", TransportMode.SUBWAY, rome, color = 0xFFE27439.toInt()).color)
        assertEquals("B", line("MEB", TransportMode.SUBWAY, rome).name)
        assertEquals("B1", line("MEB1", TransportMode.SUBWAY, rome).name)
        assertEquals("C", line("MEC", TransportMode.SUBWAY, rome).name)
        assertEquals("105", line("105", TransportMode.BUS, rome).name)
    }

    @Test
    fun `regional trains by category, long-distance trains with their number`() {
        val trenitalia = "20261127_08:35_it-trenitalia_0083_620083_0_v_62-4594-890C-0083_v_62-4594-890C-0083"
        assertEquals("REG", line("REG 12655", TransportMode.REGIONAL, trenitalia).name)
        assertEquals("RV", line("RV 4594", TransportMode.REGIONAL, trenitalia).name)
        assertEquals("SFM", line("SFM 26350", TransportMode.SUBURBAN, trenitalia).name)
        assertEquals("FR 9503", line("FR 9503", TransportMode.LONG_DISTANCE, trenitalia).name)
        assertEquals("IC 551", line("IC 551", TransportMode.LONG_DISTANCE, trenitalia).name)
        // Other operators' lines are lines already.
        assertEquals("RE80", line("RE80", TransportMode.REGIONAL, "20261127_08:43_it-Lombardia-Trenord_125510-2026-08").name)
        assertEquals("S3", line("S3", TransportMode.REGIONAL, "20261127_08:32_it-Lombardia-Trenord_1900823-2025-1").name)
    }

    @Test
    fun `operators in capitals`() {
        assertEquals("Trenitalia", Italy.operatorName("TRENITALIA"))
        assertEquals("Busitalia Veneto", Italy.operatorName("BUSITALIA VENETO"))
        assertEquals("ATM", Italy.operatorName("ATM"))
        assertEquals("Atac", Italy.operatorName("Atac"))
        assertEquals("Trenord", Italy.operatorName("Trenord"))
    }

    @Test
    fun `train numbers without the line, no trip numbers for buses`() {
        assertEquals("823", Italy.tripNumber("S3 - 823", Line("S3", TransportMode.REGIONAL)))
        assertEquals("25", Italy.tripNumber("RE_1 - 25", Line("RE1", TransportMode.REGIONAL)))
        assertEquals("9490", Italy.tripNumber("9490", Line("FR 9490", TransportMode.LONG_DISTANCE)))
        assertNull(Italy.tripNumber("27333574", Line("105", TransportMode.BUS)))
        assertNull(Italy.tripNumber("18", Line("901", TransportMode.BUS)))
    }

    private fun departure(tripId: String, line: String, number: String, minute: Int) = Departure(
        tripId = tripId, line = Line(line, TransportMode.REGIONAL), headsign = "Fiumicino Aeroporto",
        scheduled = Instant.parse("2026-11-27T07:%02d:00Z".format(minute)), estimated = null, platform = null,
        scheduledPlatform = null, cancelled = false, operator = "TRENITALIA", stopId = "it-trenitalia_08409",
        stopName = "Roma Termini", tripNumber = number,
    )

    @Test
    fun `outdated copies of Trenitalia trains go when the current one is on the board`() {
        val planned = departure("20261127_08:35_it-trenitalia_0083_10083_0_p_1-4594-532-0083_p_1-4594-532-0083", "REG 4594", "4594", 35)
        val current = departure("20261127_08:35_it-trenitalia_0083_620083_0_v_62-4594-890C-0083_v_62-4594-890C-0083", "RV 4594", "4594", 35)
        val other = departure("20261127_08:20_it-trenitalia_0083_10083_0_p_1-4592-534-0083_p_1-4592-534-0083", "REG 4592", "4592", 20)
        val bus = departure("20261127_07:52_it-Lazio-Rome_0#606-4", "105", "4594", 30)
        assertEquals(listOf(current, other, bus), Italy.withoutDuplicates(listOf(planned, current, other, bus)))
        // Alone, a "p" train is all there is.
        assertEquals(listOf(planned, other), Italy.withoutDuplicates(listOf(planned, other)))
    }

    @Test
    fun `stop names from Italian feeds, also abroad`() {
        assertEquals("Roma Termini", Italy.stopName("ROMA TERMINI"))
        assertEquals("Stazione Centrale M2 M3", Italy.stopName("stazione centrale m2 m3"))
        // A Trenitalia stop in Switzerland gets the Italian rules of its feed.
        assertEquals("Zurigo HB", OtherCountry.stopName("ZURIGO HB", feed = Italy))
        assertEquals("ZURIGO HB", OtherCountry.stopName("ZURIGO HB"))
        // DELFI names of Italian stations are left as they are.
        assertEquals("Bolzano/Bozen", Italy.stopName("Bolzano/Bozen", feed = Germany))
        // Broken encodings are repaired for every country.
        assertEquals("Bicocca (Università)", Italy.stopName("bicocca (universitÃ )"))
    }

    @Test
    fun `no town lookups and alerts without the signature`() {
        assertFalse(Italy.needsTown(NamedPlace("it-Lombardia-ATM_11916", "Ortica", 45.47, 9.25), "it-Lombardia-ATM_11915"))
        assertEquals(
            "Prov. da Stazione San Pietro: normale itinerario di linea.",
            Italy.alertText("Prov. da Stazione San Pietro: normale itinerario di linea.\n (@romamobilita)", "it").trim(),
        )
        assertEquals("La linea non fa la fermata 79711.", Italy.alertText("La linea non fa la fermata 79711. (@romamobilita)", "en"))
        assertNull(Italy.modeLabel(TransportMode.SUBWAY))
    }
}
