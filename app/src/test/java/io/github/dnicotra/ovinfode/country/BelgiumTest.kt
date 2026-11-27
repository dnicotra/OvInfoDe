package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Names, ids and positions as Transitous returned them on 2026-11-27. */
class BelgiumTest {
    private val sncbTrip = "20261127_07:06_be-sncb_88____:007::8831765:8892007:19:947:20261211"
    private val tecTrip = "20261127_07:19_be-tec_gt:tec:55536802-L_PA_2026-26_LG_N3-Sem-N-3-11"

    private fun line(name: String, mode: TransportMode, tripId: String = sncbTrip) = Belgium.line(Line(name, mode), tripId)

    @Test
    fun `SNCB trains are named by their type, with the number kept for the trip`() {
        assertEquals(Line("IC", TransportMode.REGIONAL), line("IC 3629", TransportMode.REGIONAL))
        assertEquals(Line("L", TransportMode.REGIONAL), line("L 4978", TransportMode.REGIONAL))
        assertEquals(Line("P", TransportMode.REGIONAL), line("P 7445", TransportMode.REGIONAL))
        // EuroCity trains to the Netherlands are "regional" in the feed; a few Intercity trips "suburban".
        assertEquals(Line("EC", TransportMode.LONG_DISTANCE), line("EC 9215", TransportMode.REGIONAL))
        assertEquals(Line("IC", TransportMode.REGIONAL), line("IC 4529", TransportMode.SUBURBAN))
        // S trains have line numbers already.
        assertEquals(Line("S32", TransportMode.SUBURBAN), line("S32", TransportMode.SUBURBAN))
        // Other feeds are left alone: TEC's buses, the German ICE to Brussels.
        assertEquals(Line("B2", TransportMode.BUS), line("B2", TransportMode.BUS, tripId = tecTrip))
        assertEquals(Line("ICE 11", TransportMode.LONG_DISTANCE), line("ICE 11", TransportMode.LONG_DISTANCE, tripId = "20261127_06:23_de-DELFI_3490186732"))
    }

    @Test
    fun `only trains keep their trip numbers`() {
        assertEquals("3629", Belgium.tripNumber("3629", Line("IC", TransportMode.REGIONAL)))
        assertEquals("2577", Belgium.tripNumber("2577", Line("S32", TransportMode.SUBURBAN)))
        assertNull(Belgium.tripNumber("11", Line("B2", TransportMode.BUS)))
        assertNull(Belgium.tripNumber("74", Line("T1", TransportMode.TRAM)))
        assertNull(Belgium.tripNumber("1", Line("M3", TransportMode.SUBWAY)))
    }

    @Test
    fun `TEC is TEC`() {
        assertEquals("TEC", Belgium.operatorName("LETEC"))
        assertEquals("NMBS/SNCB", Belgium.operatorName("NMBS/SNCB"))
        assertEquals("De Lijn", Belgium.operatorName("De Lijn"))
    }

    @Test
    fun `STIB headsigns in capitals give way to the final stop's name`() {
        assertEquals("Gare du Midi", Belgium.headsign("GARE DU MIDI", "Gare du Midi"))
        assertEquals("De Brouckère", Belgium.headsign("DE BROUCKERE", "De Brouckère"))
        assertEquals("Hôpital Militaire", Belgium.headsign("HOP. MILITAIRE", "Hôpital Militaire"))
        assertEquals("Zuidstation", Belgium.headsign("ZUIDSTATION", "Zuidstation"))
        // Headsigns written normally are the operator's choice.
        assertEquals("Deurne Silsburg", Belgium.headsign("Deurne Silsburg", "Borsbeek Silsburg"))
        assertEquals("UZ-VUB", Belgium.headsign("UZ-VUB", null))
    }

    @Test
    fun `TEC towns lose their capitals`() {
        fun tec(name: String) = Belgium.stopName(name, stopId = "be-tec_gs:tec:L2Tguil2")
        assertEquals("Liège, Gare des Guillemins (Tram)", tec("LIEGE Gare des Guillemins (Tram)"))
        assertEquals("Charleroi, Tirou (M)", tec("CHARLEROI Tirou (M)"))
        assertEquals("Mont-sur-Marchienne, Place", tec("MONT-SUR-MARCHIENNE Place"))
        assertEquals("Braine-l'Alleud, Gare", tec("BRAINE-L'ALLEUD Gare"))
        assertEquals("Fontaine-l'Évêque, Place", tec("FONTAINE-L'EVEQUE Place"))
        assertEquals("La Louvière, SNCB Sud - Quai C", tec("LA LOUVIERE SNCB Sud - Quai C"))
        assertEquals("Mons-lez-Liège, Église", tec("MONS-LEZ-LIEGE Église"))
        assertEquals("Grâce-Hollogne, Place", tec("GRACE-HOLLOGNE Place"))
        assertEquals("Pont-à-Celles, Gare", tec("PONT-A-CELLES Gare"))
        assertEquals("Mouscron/Moeskroen, Rue Royale/Koninklijkestraat", tec("MOUSCRON/MOESKROEN Rue Royale/Koninklijkestraat"))
        // The German towns of the east follow the German abbreviations, as everywhere.
        assertEquals("St. Vith, Vielsalmer Str.", tec("SANKT VITH Vielsalmer Strasse"))
        // The town said twice: once is enough.
        assertEquals("Liège Expo (Tram)", tec("LIEGE Liège Expo (Tram)"))
        // All in capitals: the town cannot be told from the rest.
        assertEquals("TOURNAI SNCB", tec("TOURNAI SNCB"))
        assertEquals("LA HULPE IBM", tec("LA HULPE IBM"))
        // Already readable (some translations are), or not TEC's.
        assertEquals("Liège Expo", tec("Liège, Liège Expo"))
        assertEquals("UZ Gent", Belgium.stopName("UZ Gent", stopId = "be-delijn_gs:delijn:212179"))
        assertEquals("ZNA Middelheim", Belgium.stopName("ZNA Middelheim", stopId = "be-delijn_gs:delijn:1"))
        // FlixBus copies TEC's names.
        assertEquals("Charleroi, Gare Centrale", Belgium.stopName("CHARLEROI Gare Centrale", stopId = "eu-flixbus_dcc076f4-9603-11e6-9066-549f350fcb0c"))
        assertEquals("CERIA", Belgium.stopName("CERIA", stopId = "be-stib_8721"))
    }

    @Test
    fun `stops of Belgian feeds abroad follow their feed's style`() {
        assertEquals("Aachen, Bushof", Germany.stopName("AACHEN Bushof", feed = Belgium, stopId = "be-tec_gs:tec:LaAbush*"))
        assertEquals("Aachen Hbf (DE)", Germany.stopName("Aachen Hbf (DE)", feed = Belgium, stopId = "be-sncb_8015345"))
        assertEquals("Rotterdam Centraal (NL)", Netherlands.stopName("Rotterdam Centraal (NL)", feed = Belgium, stopId = "be-sncb_8400530"))
    }

    @Test
    fun `De Lijn platforms are not part of the name`() {
        assertEquals("Antwerpen Centraal Station", Belgium.stopName("Antwerpen Centraal Station perron 2", stopId = "be-delijn_gs:delijn:103734"))
        assertEquals("Antwerpen Roosevelt", Belgium.stopName("Antwerpen Roosevelt perron B3", stopId = "be-delijn_gs:delijn:103797"))
        assertEquals("Gent Sint-Pietersstation", Belgium.stopName("Gent Sint-Pietersstation perron 22", null))
        assertEquals("Brussel-Zuid", Belgium.stopName("Brussel-Zuid", stopId = "be-sncb_S8814001"))
    }

    @Test
    fun `names in the language of the signs, unless the user reads Dutch or French`() {
        fun lang(user: String, lat: Double, lon: Double) = Belgium.requestLanguage(user, LatLon(lat, lon))
        // Flanders.
        assertEquals("nl", lang("de", 51.2172, 4.4211)) // Antwerpen-Centraal
        assertEquals("nl", lang("de", 51.0361, 3.7108)) // Gent-Sint-Pieters
        assertEquals("nl", lang("en", 50.8817, 4.7159)) // Leuven
        assertEquals("nl", lang("de", 51.2254, 2.9258)) // Oostende, on the coast
        assertEquals("nl", lang("de", 50.7336, 4.2398)) // Halle, south of Brussels
        assertEquals("nl", lang("de", 50.8977, 4.4826)) // Brussels Airport, in Zaventem
        assertEquals("nl", lang("de", 50.7563, 5.7906)) // Voeren, between Wallonia and the Netherlands
        assertEquals("nl", lang("de", 50.8240, 3.2640)) // Kortrijk
        // Wallonia and Brussels.
        assertEquals("fr", lang("de", 50.6246, 5.5667)) // Liège-Guillemins
        assertEquals("fr", lang("de", 50.7177, 4.6123)) // Wavre
        assertEquals("fr", lang("de", 50.7444, 3.2105)) // Mouscron
        assertEquals("fr", lang("en", 50.4047, 4.4386)) // Charleroi
        assertEquals("fr", lang("de", 50.6268, 6.0369)) // Eupen
        assertEquals("fr", lang("de", 50.8457, 4.3568)) // Bruxelles-Central
        assertEquals("fr", lang("de", 50.8977, 4.3367)) // Brussels, Heizel/Heysel
        assertEquals("fr", Belgium.requestLanguage("de", null))
        // Dutch and French readers get their own language everywhere.
        assertEquals("nl", lang("nl", 50.6246, 5.5667))
        assertEquals("fr", lang("fr", 51.2172, 4.4211))
    }

    @Test
    fun `destinations never need a town lookup`() {
        assertFalse(Belgium.needsTown(NamedPlace("be-stib_0631", "Gare du Midi", 50.836, 4.336), "be-stib_6"))
        assertFalse(Belgium.needsTown(NamedPlace("be-sncb_8841004", "Liège-Guillemins", 50.6246, 5.5667), "be-sncb_8813003"))
    }

    @Test
    fun `domestic trains are trains`() {
        assertEquals(R.string.mode_train, Belgium.modeLabel(TransportMode.REGIONAL))
        assertNull(Belgium.modeLabel(TransportMode.SUBWAY))
        assertNull(Belgium.modeLabel(TransportMode.SUBURBAN))
    }
}
