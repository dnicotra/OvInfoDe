package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.cleanAlertText
import io.github.dnicotra.ovinfode.util.frenchCase
import io.github.dnicotra.ovinfode.util.frenchStopName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FranceTest {
    private val sncf = "20261127_06:16_fr-horaires-sncf_FR:ServiceJourney::SN9761FERRE_1348922"
    private val idfm = "20261127_05:33_fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:TN:SNCF:5c4b9a19"
    private val tcl = "20261127_05:59_fr-lyon-tcl_302_302A-2055M_0520100"
    private val breizhgo = "20261127_07:00_fr-breizhgo-ter_123"

    private fun line(name: String, mode: TransportMode, tripId: String) = France.line(Line(name, mode), tripId)

    @Test
    fun `SNCF trains by product, the number goes with the trip`() {
        assertEquals(Line("TGV", TransportMode.LONG_DISTANCE), line("TGV INOUI 6641", TransportMode.LONG_DISTANCE, sncf))
        assertEquals(Line("OUIGO", TransportMode.LONG_DISTANCE), line("OUIGO 7829", TransportMode.LONG_DISTANCE, sncf))
        assertEquals(Line("OUIGO", TransportMode.LONG_DISTANCE), line("OUIGO_TC 7601", TransportMode.REGIONAL, sncf))
        assertEquals(Line("Lyria", TransportMode.LONG_DISTANCE), line("TGV Lyria 9761", TransportMode.LONG_DISTANCE, sncf))
        assertEquals(Line("IC", TransportMode.LONG_DISTANCE), line("IC 4300", TransportMode.LONG_DISTANCE, sncf))
        assertEquals(Line("TER", TransportMode.REGIONAL), line("TER 889652", TransportMode.REGIONAL, sncf))
        // A TER coach stays a bus.
        assertEquals(Line("TER", TransportMode.BUS), line("TER 44504", TransportMode.BUS, sncf))
        // Regional lines with a name of their own keep it.
        assertEquals("K14", line("K14", TransportMode.REGIONAL, sncf).name)
        assertEquals("TER", line("Paris - Chartres - Le Mans", TransportMode.REGIONAL, breizhgo).name)
        // Buses with a number in their name are no trains.
        assertEquals("Navette 12", line("Navette 12", TransportMode.BUS, tcl).name)
        // Other countries' trains at a French station are left to their own rules.
        assertEquals("FR 6648", line("FR 6648", TransportMode.LONG_DISTANCE, "20261127_06:08_it-trenitalia_0083_680083_0_v_68-6648").name)
    }

    @Test
    fun `Paris RER, Transilien and metro`() {
        assertEquals(Line("RER A", TransportMode.SUBURBAN), line("A", TransportMode.REGIONAL, idfm))
        assertEquals(Line("RER D", TransportMode.SUBURBAN), line("D", TransportMode.REGIONAL, idfm))
        assertEquals(Line("R", TransportMode.SUBURBAN), line("R", TransportMode.REGIONAL, idfm))
        assertEquals("M1", line("1", TransportMode.SUBWAY, idfm).name)
        assertEquals("M14", line("14", TransportMode.SUBWAY, idfm).name)
        assertEquals("M7B", line("7bis", TransportMode.SUBWAY, idfm).name)
        assertEquals("T3a", line("T3a", TransportMode.TRAM, idfm).name)
        assertEquals("91", line("91", TransportMode.BUS, idfm).name)
        // Lyon's metro lines are letters.
        assertEquals(Line("B", TransportMode.SUBWAY), line("B", TransportMode.SUBWAY, tcl))
        assertEquals(R.string.mode_suburban_fr, France.modeLabel(TransportMode.SUBURBAN))
        assertNull(France.modeLabel(TransportMode.SUBWAY))
    }

    @Test
    fun `RER missions go to their final stop`() {
        assertEquals("Cergy le Haut", France.headsign("UZAR", "Cergy le Haut"))
        assertEquals("Château de Vincennes", France.headsign("Château de Vincennes", "Château de Vincennes"))
        assertEquals("UZAR", France.headsign("UZAR", null))
        assertEquals("Lille Flandres", France.headsign("844051", "Lille Flandres"))
        assertEquals("Aéroport", France.headsign("Aéroport", "Aéroport Toulouse-Blagnac"))
    }

    @Test
    fun `trip numbers of trains and RER missions only`() {
        val tgv = Line("TGV", TransportMode.LONG_DISTANCE)
        val rer = Line("RER A", TransportMode.SUBURBAN)
        assertEquals("6641", France.tripNumber("6641", tgv))
        assertEquals("UZAR", France.tripNumber("UZAR03", rer))
        assertEquals("126603", France.tripNumber("126603", Line("RER D", TransportMode.SUBURBAN)))
        assertEquals("153602", France.tripNumber("153602-153603", Line("RER D", TransportMode.SUBURBAN)))
        assertEquals("44504", France.tripNumber("44504", Line("TER", TransportMode.BUS)))
        assertNull(France.tripNumber("RATP", Line("M1", TransportMode.SUBWAY)))
        assertNull(France.tripNumber("REF~250426~181226~Itinéraire nominal complet", Line("B", TransportMode.SUBWAY)))
        assertNull(France.tripNumber("50551478", Line("91", TransportMode.BUS)))
        assertEquals("SNCF", France.operatorName("SNCF VOYAGEURS"))
        assertEquals("Tisséo", France.operatorName("Tisséo"))
    }

    @Test
    fun `stop names`() {
        assertEquals("Paris Gare de Lyon", frenchStopName("Paris Gare de Lyon Hall 1 - 2"))
        assertEquals("Paris Gare de Lyon", frenchStopName("Paris Gare de Lyon Hall 3"))
        assertEquals("Strasbourg", frenchStopName("Strasbourg Bahnhof"))
        assertEquals("Marseille St Charles", frenchStopName("Estación de tren Marseille St Charles"))
        assertEquals("Mulhouse, Zoo", frenchStopName("MULHOUSE - Zoo"))
        assertEquals("Toulouse, Pont Matabiau", frenchStopName("TOULOUSE - Pont Matabiau"))
        assertEquals("Riedisheim, Crédit Mutuel Rue Bartholdi", frenchStopName("RIEDISHEIM - Crédit Mutuel Rue Bartholdi"))
        assertEquals("République", frenchStopName("REPUBLIQUE"))
        assertEquals("Saint-Rémi", frenchStopName("SAINT-REMI"))
        assertEquals("Mumm / Foujita", frenchStopName("MUMM / FOUJITA"))
        assertEquals("Marseille / Castellane", frenchStopName("MARSEILLE / CASTELLANE"))
        assertEquals("Rennes-Gare Routière", frenchStopName("RENNES-GARE ROUTIERE"))
        // Names written normally stay as they are.
        listOf("Gare de Lyon", "Saint-Denis - Pleyel", "Joinville-le-Pont - RER", "Arcueil – Laplace RER", "Reims - Cimetiere de l'Est", "T1", "TNB")
            .forEach { assertEquals(it, frenchStopName(it)) }
        assertEquals("Paris Gare de Lyon", France.stopName("Paris Gare de Lyon Hall 1 - 2"))
        assertEquals("Square des Meres", frenchStopName("Square Des Meres"))
        assertEquals("Paris Gare de Lyon", France.stopName("PARIS GARE DE LYON", feed = Italy))
    }

    @Test
    fun `french capitals`() {
        assertEquals("Cimetière de l'Est", frenchCase("CIMETIERE DE L'EST"))
        assertEquals("L'Étoile", frenchCase("L'ETOILE"))
        assertEquals("Gare SNCF", frenchCase("GARE SNCF"))
        assertEquals("CHU Nord", frenchCase("CHU NORD"))
        assertEquals("St Exupéry", frenchCase("ST EXUPéRY"))
        assertEquals("Place à Côte", frenchCase("PLACE A COTE"))
        assertEquals("Rue A. Briand", frenchCase("RUE A. BRIAND"))
        assertEquals("Châlons-en-Champagne", frenchCase("CHâLONS-EN-CHAMPAGNE"))
    }

    @Test
    fun `alerts in HTML`() {
        val html = "<p>P&#233;riode : en semaine &#224; partir de 21h50.<br><br>Dates : du lundi 16 au vendredi 27 novembre.</p><p>Motif : travaux.</p>"
        assertEquals("Période : en semaine à partir de 21h50.\n\nDates : du lundi 16 au vendredi 27 novembre.\n\nMotif : travaux.", cleanAlertText(html).trim())
        assertEquals("a < b", cleanAlertText("a < b"))
    }

    @Test
    fun `destinations never need a town`() {
        assertFalse(France.needsTown(NamedPlace("fr-lyon-tcl_S5520", "Debourg", 45.73, 4.83), null))
    }
}
