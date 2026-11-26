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
        assertSame(OtherCountry, of("de-DELFI_fr:24067:1284", 48.58534, 7.73407)) // Strasbourg
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
        assertSame(OtherCountry, of("be-sncb_S8814001", 50.83571, 4.33653)) // Brussels
        assertSame(OtherCountry, of("ch-opentransportdataswiss26_Parentch:1:sloid:10", 47.54741, 7.58956)) // Basel SBB
        assertSame(OtherCountry, of("at-Railway-Current-Reference-Data-2026_Pat:45:50002", 47.81306, 13.04513)) // Salzburg Hbf
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
        // Stops abroad in the same feed: Germany by its id, Belgium and France without rules.
        assertSame(Germany, of(atp + "000500000019", 49.707375, 6.550666)) // Igel
        assertSame(Germany, of(atp + "000500000075", 49.689728, 6.46192)) // Temmels, across the Moselle
        assertSame(OtherCountry, of(atp + "000300000017", 49.563934, 5.82912)) // Athus, Gare
        assertSame(OtherCountry, of(atp + "000400000106", 49.456738, 6.07897)) // Volmerange-les-Mines, Gare
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
        assertSame(OtherCountry, of("nl-OpenOV_x", 51.2, 2.9)) // Ostend
    }

    @Test
    fun `trips belong to the country of their feed`() {
        assertSame(Netherlands, Countries.ofTrip("20261126_09:27_nl-OpenOV_395288670"))
        assertSame(Netherlands, Countries.ofTrip("20261126_09:12_nl-OpenOV_2026-11-26:IFF:IC:303531"))
        assertSame(Germany, Countries.ofTrip("20261126_07:03_de-DELFI_3490184955"))
        assertNull(Countries.ofTrip("20261126_10:00_be-sncb_88____:L72"))
        assertNull(Countries.ofTrip("eu-flixbus_123"))
        assertNull(Countries.ofTrip(null))
    }

    @Test
    fun `outlines decode to plausible shapes`() {
        val amsterdam = LatLon(52.37, 4.89)
        assertTrue(amsterdam in Netherlands.outline)
        assertTrue(LatLon(52.52, 13.40) in Germany.outline) // Berlin
        assertTrue(LatLon(48.14, 11.58) in Germany.outline) // Munich
        assertTrue(LatLon(49.61, 6.13) in Luxembourg.outline) // Luxembourg City
        assertFalse(LatLon(49.757, 6.651) in Luxembourg.outline) // Trier
        assertTrue(Netherlands.outline.isNear(LatLon(52.37, 4.70), km = 10.0)) // North Sea off Zandvoort
        assertEquals(listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)), decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@"))
        // Line breaks of the generated source are ignored.
        assertEquals(decodePolyline("_p~iF~ps|U_ulLnnqC"), decodePolyline("_p~iF~ps|U\n        _ulLnnqC"))
    }

    @Test
    fun `codes`() {
        assertSame(Netherlands, Countries.byCode("NL"))
        assertSame(Germany, Countries.byCode("de"))
        assertSame(Luxembourg, Countries.byCode("LU"))
        assertNull(Countries.byCode("be"))
        assertNull(Countries.byCode(null))
    }
}
