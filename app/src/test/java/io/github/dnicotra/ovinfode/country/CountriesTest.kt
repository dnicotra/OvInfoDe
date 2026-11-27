package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.data.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stop ids and positions as Transitous returned them on 2026-11-26. */
class CountriesTest {

    private fun of(id: String?, lat: Double, lon: Double) = Countries.of(id, lat, lon)

    @Test
    fun `a country code in the stop id decides, whatever the feed or position`() {
        assertSame(Germany, of("de-DELFI_de:08111:6115:2:3", 48.7855, 9.1824))
        assertSame(Germany, of("at-Railway-Current-Reference-Data-2026_de:08221:1160:1:1", 49.4035, 8.6748))
        // Lindau-Insel lies in Lake Constance, outside every outline.
        assertSame(Germany, of("at-Railway-Current-Reference-Data-2026_de:09776:1546:1:1", 47.54539, 9.68079))
        assertSame(Netherlands, of("de-DELFI_nl:9:18124", 52.22256, 6.89023)) // Enschede in the German feed
        assertSame(Netherlands, of("nl-OpenOV_NL:S:30009577", 52.3723, 4.8925)) // Amsterdam, Rokin
        assertSame(France, of("de-DELFI_fr:24067:1284", 48.58534, 7.73407)) // Strasbourg
        // A prefix that is no known country code says nothing: Baarle-Nassau in the Belgian De Lijn feed.
        assertSame(Netherlands, of("be-delijn_gs:delijn:108024", 51.44177, 4.90492))
        // Even with a misleading position.
        assertSame(Germany, of("de-DELFI_de:05154:36119_G", 52.37, 4.89))
    }

    @Test
    fun `other ids go by position`() {
        // Dutch stations in the German DELFI feed and in international feeds.
        assertSame(Netherlands, of("de-DELFI_000008400058", 52.37919, 4.899431)) // Amsterdam Centraal
        assertSame(Netherlands, of("de-DELFI_000008400483", 52.30628, 6.93313)) // Oldenzaal
        assertSame(Netherlands, of("fr-eurostar-gtfs-plan-de-transport-et-temps-reel_rotterdam_centraal_station_area", 51.923676, 4.470496))
        assertSame(Netherlands, of("eu-blablacar-bus_GRN", 53.2105, 6.5641)) // Groningen
        assertSame(Netherlands, of("nl-OpenOV_4016298", 53.04954, 4.79466)) // Den Burg, Texel
        // German stations in the Dutch OpenOV feed and in a Belgian one.
        assertSame(Germany, of("nl-OpenOV_stoparea:18164", 51.32718, 6.2027)) // Kaldenkirchen
        assertSame(Germany, of("nl-OpenOV_stoparea:18091", 53.16361, 7.34272)) // Weener
        assertSame(Germany, of("be-sncb_8015345", 50.76776, 6.09139)) // Aachen Hbf
        assertSame(Germany, of("eu-flixbus_dcbbfc7d-9603-11e6-9066-549f350fcb0c", 54.41175, 13.43162)) // Bergen auf Rügen
        assertSame(Germany, of(null, 51.8338, 6.25495)) // Emmerich
        assertSame(Netherlands, of(null, 51.36468, 6.171236)) // Venlo
        assertSame(Netherlands, of(null, 51.92286, 6.07266)) // Zevenaar
        // Neighbours without rules of their own.
        assertSame(OtherCountry, of("ch-opentransportdataswiss26_Parentch:1:sloid:10", 47.54741, 7.58956)) // Basel SBB
        assertSame(OtherCountry, of("at-Railway-Current-Reference-Data-2026_Pat:45:50002", 47.81306, 13.04513)) // Salzburg Hbf
    }

    @Test
    fun `french stops in all their feeds`() {
        assertSame(France, of("fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:73626", 48.84457, 2.37515)) // Paris, Gare de Lyon
        assertSame(France, of("ch-opentransportdataswiss26_Parent8768600", 48.84484, 2.37407)) // the same, Swiss feed
        assertSame(France, of("be-sncb_8728600", 50.63333, 3.066667)) // Lille Flandres
        assertSame(France, of("fr-horaires-sncf_FR::LMU:7429fb60-cb0a-11e8-8bfa-f784c1c7c611:", 43.611206, 1.453616)) // Toulouse Matabiau
        assertSame(France, of("fr-donnees-statiques-et-dynamiques-du-reseau-de-transport-lignes-dazur_123", 43.6966, 7.2711)) // Nice
        assertSame(France, of(null, 42.3063, 9.1500)) // Corte, Corsica
        // Luxembourg's ids say "France" for Volmerange-les-Mines, a kilometre from the border.
        assertSame(France, of("lu-administration-des-transports-publics_000400000034", 49.4446, 6.0806))
        // Neighbours stay what they are.
        assertSame(Belgium, of("fr-horaires-sncf_FR::LMO:c2464480-fa0b-11e8-8bfa-f784c1c7c611:", 50.8357, 4.3360)) // Bruxelles-Midi
        assertSame(OtherCountry, of("fr-horaires-sncf_FR::LMO:70e98500-3a69-11e8-8bfa-f784c1c7c611:", 46.2102, 6.1424)) // Genève
        assertSame(OtherCountry, of(null, 43.7384, 7.4246)) // Monaco
        assertSame(Germany, of("fr-horaires-sncf_FR::LMO:71df2fa0-3a69-11e8-8bfa-f784c1c7c611:", 48.4764, 7.9466)) // Offenburg
    }

    @Test
    fun `luxembourg stops go by their id, also right on the border rivers`() {
        val atp = "lu-administration-des-transports-publics_"
        // Towns on the Our, Sûre and Moselle, which the simplified outlines put in Germany.
        assertSame(Luxembourg, of(atp + "000130301006", 49.933483, 6.212653)) // Vianden, Gare
        assertSame(Luxembourg, of(atp + "000170501016", 49.81023, 6.417321)) // Echternach, Gare Centre
        assertSame(Luxembourg, of(atp + "000210701006", 49.542736, 6.368293)) // Remich, Gare routière
        assertSame(Luxembourg, of(atp + "000180703009", 49.71295, 6.499305)) // Wasserbillig, Gare
        assertSame(Luxembourg, of(atp + "000110407001", 50.036484, 6.129956)) // Rodershausen, Kiirch
        assertSame(Luxembourg, of(atp + "000200405020", 49.6115, 6.126)) // Hamilius
        // Stops abroad in the same feed: Germany and Belgium by their ids, France without rules.
        assertSame(Germany, of(atp + "000500000019", 49.707375, 6.550666)) // Igel
        assertSame(Germany, of(atp + "000500000075", 49.689728, 6.46192)) // Temmels, across the Moselle
        assertSame(Belgium, of(atp + "000300000017", 49.563934, 5.82912)) // Athus, Gare
        assertSame(France, of(atp + "000400000106", 49.456738, 6.07897)) // Volmerange-les-Mines, Gare
        // Luxembourg stations in the German feed have "lu:" ids, in the French and Belgian ones none.
        assertSame(Luxembourg, of("de-DELFI_lu:25000:NVBW_003", 49.59981, 6.13413))
        assertSame(Luxembourg, of("fr-horaires-sncf_FR::LMU:c24c10e0-fa0b-11e9-b755-b1228bd14244:", 49.5997, 6.1347))
        assertSame(Luxembourg, of("be-sncb_8200100", 49.6, 6.1333))
        assertSame(Luxembourg, Countries.ofTrip("20261127_09:33_lu-administration-des-transports-publics_24658350"))
    }

    @Test
    fun `stops just off the outline keep the country of their feed`() {
        // Vlissingen station is at the harbour, Schiermonnikoog village outside the simplified island.
        assertSame(Netherlands, of("nl-OpenOV_stoparea:18286", 51.44413, 3.59557))
        assertSame(Netherlands, of("nl-OpenOV_4262468", 53.46864, 6.20321))
        // Not for feeds of other countries, and not far away.
        assertSame(OtherCountry, of("eu-flixbus_x", 51.44413, 3.59557))
        assertSame(France, of("nl-OpenOV_x", 50.6366, 3.0705)) // Lille
        // De Lijn's "gs:delijn:" ids carry no country code, so the feed counts: the end of Blankenberge pier.
        assertSame(Belgium, of("be-delijn_gs:delijn:x", 51.3290, 3.1300))
    }

    @Test
    fun `italian stops`() {
        // South Tyrol (STA) and DELFI write Italian stop ids with a country code, like German DHIDs.
        assertSame(Italy, of("it-Bolzano-STA_it:22021:2143:0:5235", 46.4775, 11.3305))
        assertSame(Italy, of("de-DELFI_it:22021:468", 46.4967, 11.3579))
        // Other ids go by position: Trenitalia, ATM, Rome, the Austrian railway data for Bologna.
        assertSame(Italy, of("it-trenitalia_08409:station", 41.9005, 12.502))
        assertSame(Italy, of("it-Lombardia-ATM_DUOMO M3", 45.4639, 9.1886))
        assertSame(Italy, of("at-Railway-Current-Reference-Data-2026_Pit:22015:116", 44.5059, 11.3433))
        assertSame(Italy, of("it-Veneto-Venezia-Ferries_5166", 45.4380, 12.3359)) // Rialto
        assertSame(Italy, of(null, 38.1100, 13.3400)) // Palermo
        assertSame(Italy, of(null, 35.5000, 12.6050)) // Lampedusa
        assertSame(Italy, of(null, 41.2140, 9.4070)) // La Maddalena
        // Off the simplified coast or on islands the outline lacks: Italian feeds only.
        assertSame(Italy, of("it-Veneto-Venezia-Ferries_x", 45.4853, 12.4167)) // Burano
        assertSame(Italy, of("it-Campania-EAV_068040", 40.7551, 14.0123)) // Procida
        assertSame(Italy, of("it-trenitalia_12345", 39.2170, 9.1100)) // Cagliari
        assertSame(OtherCountry, of("eu-flixbus_x", 40.7551, 14.0123))
        // San Marino and the Vatican lie inside the outline, and speak Italian.
        assertSame(Italy, of(null, 43.9360, 12.4470))
        // Neighbours.
        assertSame(OtherCountry, of("it-trenitalia_x", 47.3780, 8.5400)) // Zürich HB
        assertSame(France, of(null, 43.7046, 7.2619)) // Nice
        assertSame(OtherCountry, of(null, 46.0050, 8.9470)) // Lugano
        assertSame(OtherCountry, of(null, 47.2630, 11.4010)) // Innsbruck
        assertSame(OtherCountry, of(null, 35.8970, 14.5130)) // Valletta
    }

    @Test
    fun `stops belong to the country of their feed for its naming rules`() {
        assertSame(Italy, Countries.ofFeed("it-trenitalia_01700:station"))
        assertSame(Italy, Countries.ofFeed("it-Lombardia-ATM_CENTRALE FS"))
        assertSame(Germany, Countries.ofFeed("de-DELFI_it:22021:468"))
        assertNull(Countries.ofFeed("eu-flixbus_123"))
        assertNull(Countries.ofFeed("ch-opentransportdataswiss26_Parentch:1:sloid:10"))
        assertNull(Countries.ofFeed(null))
    }

    @Test
    fun `trips belong to the country of their feed`() {
        assertSame(Italy, Countries.ofTrip("20261127_08:35_it-trenitalia_0083_620083_0_v_62-4594-890C-0083_v_62-4594-890C-0083"))
        assertSame(Italy, Countries.ofTrip("20261126_24:08_it-Piemonte-Gruppo-Torinese-Trasporti_28557163U"))
        assertSame(Netherlands, Countries.ofTrip("20261126_09:27_nl-OpenOV_395288670"))
        assertSame(Netherlands, Countries.ofTrip("20261126_09:12_nl-OpenOV_2026-11-26:IFF:IC:303531"))
        assertSame(Germany, Countries.ofTrip("20261126_07:03_de-DELFI_3490184955"))
        assertSame(Belgium, Countries.ofTrip("20261127_07:06_be-sncb_88____:007::8831765:8892007:19:947:20261211"))
        assertSame(Belgium, Countries.ofTrip("20261127_07:19_be-tec_gt:tec:55536802-L_PA_2026-26_LG_N3-Sem-N-3-11"))
        assertSame(France, Countries.ofTrip("20261127_05:15_fr-eurostar-gtfs-plan-de-transport-et-temps-reel_9414"))
        assertNull(Countries.ofTrip("20261127_05:15_ch-opentransportdataswiss26_9414"))
        assertNull(Countries.ofTrip("eu-flixbus_123"))
        assertNull(Countries.ofTrip(null))
    }

    @Test
    fun `outlines decode to plausible shapes`() {
        val amsterdam = LatLon(52.37, 4.89)
        assertTrue(amsterdam in Netherlands.outline)
        assertTrue(LatLon(52.52, 13.40) in Germany.outline) // Berlin
        assertTrue(LatLon(48.14, 11.58) in Germany.outline) // Munich
        assertTrue(LatLon(41.90, 12.50) in Italy.outline) // Rome
        assertTrue(LatLon(49.61, 6.13) in Luxembourg.outline) // Luxembourg City
        assertFalse(LatLon(49.757, 6.651) in Luxembourg.outline) // Trier
        assertTrue(Netherlands.outline.isNear(LatLon(52.37, 4.70), km = 10.0)) // North Sea off Zandvoort
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@"))
        // Line breaks of the generated source are ignored.
        assertEquals(decodePolyline("_p~iF~ps|U_ulLnnqC"), decodePolyline("_p~iF~ps|U\n        _ulLnnqC"))
    }

    @Test
    fun `belgian stops in all their feeds`() {
        assertSame(Belgium, of("be-sncb_S8814001", 50.83571, 4.33653)) // Bruxelles-Midi
        assertSame(Belgium, of("be-stib_6", 50.8498, 4.3525)) // De Brouckère
        assertSame(Belgium, of("be-delijn_gs:delijn:103734", 51.2189, 4.42132)) // Antwerpen Centraal Station
        assertSame(Belgium, of("be-delijn_gs:delijn:501508", 51.09729, 2.58252)) // De Panne Esplanade, coast tram
        assertSame(Belgium, of("be-tec_tec:s-liegegaredesguilleminstram", 50.6249, 5.5686))
        // Belgian stations in Dutch and German feeds.
        assertSame(Belgium, of("nl-OpenOV_stoparea:17794", 50.8454, 4.3573)) // Brussel-Centraal
        assertSame(Belgium, of("de-DELFI_000008800007", 51.2158, 4.4212)) // Antwerpen-Centraal
        // Stations abroad in the SNCB feed.
        assertSame(Netherlands, of("be-sncb_8400530", 51.92485, 4.46931)) // Rotterdam Centraal
        assertSame(Netherlands, of("be-sncb_8400526", 51.54083, 4.45869)) // Roosendaal
        assertSame(France, of("be-sncb_8728600", 50.63333, 3.06667)) // Lille Flandres
        // At the Dutch border.
        assertSame(Belgium, of("be-sncb_S8821402", 51.46277, 4.45132)) // Essen
        assertSame(Netherlands, of("nl-OpenOV_stoparea:17963", 51.54031, 4.45902)) // Roosendaal
    }

    @Test
    fun `codes`() {
        assertSame(Netherlands, Countries.byCode("NL"))
        assertSame(Germany, Countries.byCode("de"))
        assertSame(Italy, Countries.byCode("IT"))
        assertSame(Luxembourg, Countries.byCode("LU"))
        assertSame(Belgium, Countries.byCode("be"))
        assertSame(France, Countries.byCode("FR"))
        assertNull(Countries.byCode("ch"))
        assertNull(Countries.byCode(null))
    }
}
