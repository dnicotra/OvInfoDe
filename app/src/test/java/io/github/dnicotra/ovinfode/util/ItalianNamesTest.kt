package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names as the Italian feeds in Transitous wrote them on 2026-11-27. */
class ItalianNamesTest {

    private fun check(expected: String, name: String) = assertEquals(name, expected, italianStopName(name))

    @Test
    fun `names in capitals get the usual spelling`() {
        check("Roma Termini", "ROMA TERMINI")
        check("Milano Centrale", "MILANO CENTRALE")
        check("Reggio di Calabria Centrale", "REGGIO DI CALABRIA CENTRALE")
        check("Fiumicino Aeroporto", "FIUMICINO AEROPORTO")
        check("Formia-Gaeta", "FORMIA-GAETA")
        check("Chiusi-Chianciano Terme", "CHIUSI-CHIANCIANO TERME")
        check("Parco di Centocelle (MC)", "PARCO DI CENTOCELLE (MC)")
        check("Arco di Travertino (MA)", "ARCO DI TRAVERTINO (MA)")
        check("San Carlo - Trieste e Trento", "SAN CARLO - TRIESTE E TRENTO")
        check("Nervi/Palazzo Sport", "NERVI/PALAZZO SPORT")
        check("Mirafiori Nord, Via Allason (Poste)", "MIRAFIORI NORD, VIA ALLASON (POSTE)")
        check("Dei Capasso", "DEI CAPASSO")
        check("Torino - Porta Nuova - Via Nizza", "TORINO - PORTA NUOVA  - VIA NIZZA")
        check("Canneto sull'Oglio", "CANNETO SULL'OGLIO")
        check("L'Aquila", "L'AQUILA")
        check("Monte Sant'Angelo", "MONTE SANT'ANGELO")
        check("Piazza d'Armi", "PIAZZA D'ARMI")
        check("Castelnuovo (AT)", "CASTELNUOVO (AT)")
    }

    @Test
    fun `abbreviations codes and numerals are kept`() {
        check("Centrale FS", "CENTRALE FS")
        check("Termini (MA-MB-FS)", "TERMINI (MA-MB-FS)")
        check("XX Settembre", "XX SETTEMBRE")
        check("Vittorio Emanuele II", "VITTORIO EMANUELE II")
        check("Via XXIV Maggio", "VIA XXIV MAGGIO")
        check("EUR Fermi", "EUR FERMI")
        check("Duomo M1", "DUOMO M1")
        check("Stabilimento GTT Venaria", "Fermata 404 - STABILIMENTO GTT VENARIA")
        check("Brignole FS", "BRIGNOLE FS/")
        check("Brin/Metro", "BRIN/METRO")
        check("Lido S.M.E. \"D\"", "Lido S.M.E. \"D\"")
        check("Laives Z.I., Capolinea", "Laives Z.I., Capolinea")
    }

    @Test
    fun `shortened words`() {
        check("L.go Preneste", "L.GO PRENESTE")
        check("P.za Stazione S. Pietro (FL)", "P.ZA STAZIONE S. PIETRO (FL)")
        check("Staz.ne Metro Cipro (MA)", "STAZ.NE METRO CIPRO (MA)")
        check("Firenze S. Maria Novella", "FIRENZE S.MARIA NOVELLA")
        check("Venezia S. Lucia", "VENEZIA S.LUCIA")
        check("Borgo S. Lorenzo", "BORGO S.LORENZO")
        check("Napoli C.le", "NAPOLI C.LE")
        check("P.te Matteotti", "P.TE MATTEOTTI")
        check("Loc. Rialto", "LOC. RIALTO")
    }

    @Test
    fun `an apostrophe for a missing accent becomes the accent`() {
        check("Cefalù", "CEFALU'")
        check("Cinecittà (MA)", "CINECITTA' (MA)")
        check("Città Giardino, Via Grosso", "CITTA' GIARDINO, VIA GROSSO")
        check("Via Varè", "via vare'")
    }

    @Test
    fun `names in lower case get capitals`() {
        check("Stazione Centrale M2 M3", "stazione centrale m2 m3")
        check("P.le Cim. Monumentale Via Bramante", "p.le cim.monumentale via bramante")
        check("P.ta Genova M2", "p.ta genova m2")
        check("Q.re Bicocca", "q.re bicocca")
        check("V.le Molise", "v.le molise")
        check("Rozzano (Via G. Rossa)", "rozzano (via g.rossa)")
        check("Roserio (Ospedale Sacco)", "roserio (ospedale sacco)")
        check("Teatro alla Scala", "teatro alla scala")
        check("Lotto M1 M5", "lotto m1 m5")
        check("Cadorna FN M1 M2", "cadorna fn m1 m2")
        check("Bicocca (Università)", "bicocca (università)")
    }

    @Test
    fun `names written with care keep their spelling`() {
        check("Milano Cadorna", "Milano Cadorna")
        check("Bologna C.le/AV", "Bologna C.le/AV")
        check("Napoli P. Garibaldi", "Napoli P. Garibaldi")
        check("Malpensa Aeroporto T2", "Malpensa Aeroporto T2")
        check("Rialto \"A\"", "Rialto \"A\"")
        check("Bolzano, NOI", "Bolzano, NOI")
        check("Laveno Mombello Lago", "Laveno Mombello Lago")
        // Only the words in capitals that are no abbreviations change.
        check("Firenze Stazione SMN", "FIRENZE Stazione SMN")
        check("Pisa stazione", "PISA stazione")
    }

    @Test
    fun `feed codes go`() {
        // GTT stop numbers and the terminus mark of destinations.
        check("Porta Nuova", "Fermata 252 - PORTA NUOVA")
        check("Stati Uniti", "Fermata 3456 - STATI UNITI CAP")
        check("De Gasperi", "Fermata 2461 - DE GASPERI CAP")
        // Cotral stop codes.
        check("Roma, Termini FS (Giolitti)", "ROMA | Termini FS (Giolitti) # f14637")
        check("Montefiascone, piazzale Roma", "MONTEFIASCONE | piazzale Roma # 1068")
        // STA names stations after their town twice.
        check("Roma Termini", "Roma, Stazione di Roma Termini")
        check("Venezia Santa Lucia", "Venezia, Stazione di Venezia Santa Lucia")
        check("Bolzano, Stazione", "Bolzano, Stazione di Bolzano")
        check("Ora, Stazione", "Ora, Stazione")
    }

    @Test
    fun `every recorded Italian name is recased safely`() {
        val names = Fixtures.italian
            .flatMap { file -> Regex(""""(?:name|headsign)"\s*:\s*"([^"]+)"""").findAll(Fixtures.text(file)).map { it.groupValues[1] } }
            .toSortedSet()
        assertTrue("names found", names.size > 80)
        val shouting = Regex("""\p{Lu}{4,}""")
        for (name in names) {
            val fixed = italianStopName(repairEncoding(name))
            println("$name → $fixed")
            assertEquals("not idempotent: $name", fixed, italianStopName(fixed))
            assertFalse("still in capitals: $name → $fixed", shouting.containsMatchIn(fixed))
            assertTrue("starts in lower case: $fixed", !fixed[0].isLowerCase())
        }
    }
}
