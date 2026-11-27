package io.github.dnicotra.ovinfode.util

import java.text.Normalizer

/*
 * Stop names of French feeds. Most networks write them normally ("Gare de Lyon", "Commerce"),
 * but some write everything in capitals without accents (Grand Reims: "REPUBLIQUE", "SAINT-REMI";
 * Soléa in Mulhouse: "PARC WALLACH") or put the town in capitals in front ("MULHOUSE - Zoo",
 * Lio in Occitania: "TOULOUSE - Pont Matabiau"). SNCF adds the hall to Paris Gare de Lyon
 * ("Paris Gare de Lyon Hall 1 - 2"), DELFI calls Strasbourg station "Strasbourg Bahnhof".
 */

// "Paris Gare de Lyon Hall 1 - 2", "… Hall 3": where SNCF shows its trains before the track is known.
private val sncfHall = Regex("""\s+Hall\s+\d+(?:\s*-\s*\d+)?$""")

// The Spanish AVE feed: "Estación de tren Marseille St Charles".
private val spanishStation = Regex("""^Estación de tren\s+""")

// DELFI names French stations the German way: "Strasbourg Bahnhof".
private val germanStation = Regex("""\s+Bahnhof$""")

// "MULHOUSE - Zoo", "TOULOUSE - Pont Matabiau": a town in capitals, then the stop.
private val townInCapitals = Regex("""^([\p{Lu}'’\- ]+?)\s+[-–]\s+(.+)$""")

/** The usual spelling of a French stop name (see above). */
fun frenchStopName(name: String): String {
    var result = name.replace(sncfHall, "").replace(spanishStation, "").replace(germanStation, "")
    townInCapitals.matchEntire(result)?.let { match ->
        val (town, stop) = match.destructured
        if (stop.any { it.isLowerCase() } && isCapitals(town)) return frenchCase(town) + ", " + stop
    }
    if (isCapitals(result)) result = frenchCase(result)
    // "Gare De Lyon", "Square Des Meres" (Ilévia; Italian feeds' rules for French stations).
    return result.replace(capitalisedParticle) { it.value.lowercase() }
}

// "De", "Du", "Des" inside a name, followed by a word; not at the start or after " - " ("Saint-Denis - La Plaine").
private val capitalisedParticle = Regex("""(?<=\p{L}\.? )(?:De|Du|Des)(?= \p{L})""")

/** Written all in capitals: at least two capital letters in a row and no lower case ("FRAC", not "A" or "T1"). */
private fun isCapitals(text: String): Boolean =
    text.none { it.isLowerCase() } && capitalsRun.containsMatchIn(text)

private val capitalsRun = Regex("""\p{Lu}{2}""")

private val word = Regex("""\p{L}+""")

/**
 * "SAINT-REMI" → "Saint-Rémi", "CIMETIERE DE L'EST" → "Cimetière de l'Est", "MUMM / FOUJITA" →
 * "Mumm / Foujita". Abbreviations (no vowel: "SNCF", "RD"; or known: "CHU", "ZAC") stay in capitals.
 */
fun frenchCase(capitals: String): String {
    var first = true
    return word.replace(capitals) { match ->
        val w = match.value
        val lower = w.lowercase()
        val next = capitals.getOrNull(match.range.last + 1)
        val spelled = when {
            // "L'EST", "D'ARC": the elided article in lower case (except at the start), the word after it capitalised.
            next != null && next in "'’" && lower in setOf("l", "d") -> if (first) lower.uppercase() else lower
            w in abbreviations -> abbreviations.getValue(w)
            w.none { it in VOWELS } -> w
            // "A." is an initial, "A" alone the preposition.
            !first && lower in particles && !(lower == "a" && next == '.') -> particles.getValue(lower)
            else -> accented[w] ?: lower.replaceFirstChar { it.titlecase() }
        }
        first = false
        spelled
    }
}

private const val VOWELS = "AEIOUYÀÂÄÉÈÊËÎÏÔÖÙÛÜŸŒÆ"

private val particles = mapOf(
    "le" to "le", "la" to "la", "les" to "les", "de" to "de", "des" to "des", "du" to "du",
    "sur" to "sur", "sous" to "sous", "lès" to "lès", "lez" to "lez", "au" to "au", "aux" to "aux",
    "en" to "en", "et" to "et", "a" to "à", "à" to "à",
)

private val abbreviations = listOf(
    "CHU", "CHR", "CHI", "ZA", "ZI", "ZAC", "ZAE", "ZUP", "IUT", "IME", "EHPAD", "LEP", "CES", "CFA",
    "RER", "TER", "TGV", "TNB", "FRAC", "ESAT", "UFR", "EDF", "CAF", "CPAM", "AFPA", "INSA", "ENSAM", "ASPTT",
).associateWith { it } + mapOf("ST" to "St", "STE" to "Ste", "DR" to "Dr", "MAL" to "Mal", "GAL" to "Gal")

/**
 * Words of French stop names that are written with accents; capitals often drop them. Words missing
 * here simply stay without accents.
 */
private val accented: Map<String, String> = listOf(
    "Abbé", "Aéroport", "Aérogare", "Allée", "Allées", "André", "Bâle", "Bégonias", "Bibliothèque", "Bruyères",
    "Carrière", "Carrières", "Cathédrale", "Cèdres", "Château", "Châtelet", "Chêne", "Chênes",
    "Cimetière", "Cité", "Clément", "Collège", "Côte", "Côtes", "Département", "Départementale", "Déportés",
    "Échangeur", "École", "Écoles", "Économie", "Église", "Élysée", "Énergie", "Entrée", "Épine", "Étang",
    "Étienne", "Étoile", "Évêché", "Fédération", "Forêt", "Frères", "Fusillés", "Général", "Geneviève",
    "Gérard", "Gué", "Hélène", "Hêtres", "Hôpital", "Hôtel", "Libération", "Liberté", "Lycée", "Maréchal",
    "Marché", "Médecin", "Médiathèque", "Mémorial", "Mères", "Musée", "Opéra", "Orée", "Pépinière",
    "Pères", "Périphérique", "Pôle", "Pré", "Préfecture", "Préfet", "Président", "Prés", "Prévoyance",
    "Prieuré", "Région", "Régional", "Rémi", "Rémy", "République", "Résidence", "Résistance", "Révolution",
    "Rivière", "Routière", "Routières", "Sébastien", "Sénat", "Thérèse", "Théâtre", "Université", "Vallée", "Zénith",
).associateBy { accentedWord ->
    Normalizer.normalize(accentedWord, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").uppercase()
}
