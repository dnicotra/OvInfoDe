package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import java.text.Normalizer

/**
 * Belgium: four national feeds, one per operator. SNCB/NMBS (trains, "be-sncb"), STIB/MIVB
 * (Brussels, "be-stib"), De Lijn (Flanders, "be-delijn") and TEC (Wallonia, "be-tec"). Each
 * names its stops its own way, and the country speaks Dutch in Flanders, French in Wallonia
 * and both in Brussels. See docs/research.md for the recorded data behind these rules.
 */
object Belgium : Country("be") {
    override val outline = Outline(CountryOutlines.BE)
    private val flanders = Outline(CountryOutlines.BE_VLG)
    private val brussels = Outline(CountryOutlines.BE_BRU)

    /**
     * Train stations are named after their town, De Lijn stops "Town Stop", TEC stops "TOWN Stop"
     * and STIB destinations lie in Brussels: destinations never need a town lookup.
     */
    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean = false

    /**
     * SNCB, STIB and TEC translate their names into Dutch and French properly. Asked for German
     * or English, SNCB answers with both languages, abbreviated to fit its displays
     * ("Brus.-/ Brux-Centr.", "Leuven / Louvain"). Everyone else gets the names on the signs:
     * Dutch in Flanders, French in Wallonia and Brussels (where most signs list French first).
     */
    override fun requestLanguage(userLanguage: String, at: LatLon?): String = when {
        userLanguage == "nl" || userLanguage == "fr" -> userLanguage
        at == null || at in brussels -> "fr"
        // The provinces are simplified one by one, so a narrow margin closes the gaps between them.
        flanders.isNear(at, km = 0.5) -> "nl"
        else -> "fr"
    }

    // SNCB names its trains by type and number ("IC 2003", "L 4978", "EC 9215"); on platforms and in
    // its app the type is the line, and the number goes with the trip. S trains have line numbers.
    private val sncbTrain = Regex("""^([A-Z]{1,5}) \d{1,6}$""")
    private val sncbLongDistance = setOf("EC", "ICE", "THA", "EUR", "EST", "TGV", "NJ")
    private val sncbDomestic = setOf("IC", "L", "P")

    override fun line(line: Line, tripId: String): Line {
        if (!tripId.contains("_be-sncb_")) return line
        val type = sncbTrain.find(line.name)?.groupValues?.get(1) ?: return line
        val mode = when (type) {
            in sncbLongDistance -> TransportMode.LONG_DISTANCE
            // A few Intercity trips come as suburban trains in the feed.
            in sncbDomestic -> TransportMode.REGIONAL
            else -> line.mode
        }
        return line.copy(name = type, mode = mode)
    }

    /** STIB writes its headsigns in capitals and abbreviated ("HOP. MILITAIRE"); its final stops have the proper name. */
    override fun headsign(headsign: String, finalStop: String?): String =
        if (finalStop != null && headsign.none { it.isLowerCase() } && finalStop.any { it.isLowerCase() }) finalStop else headsign

    // TEC's agency is "LETEC" in its feed; passengers know it as TEC.
    override fun operatorName(name: String): String = if (name == "LETEC") "TEC" else name

    /** Train numbers are known to passengers; De Lijn's and TEC's trip numbers are internal. */
    override fun tripNumber(number: String, line: Line): String? =
        number.takeIf { line.mode == TransportMode.LONG_DISTANCE || line.mode == TransportMode.REGIONAL || line.mode == TransportMode.SUBURBAN }

    // De Lijn names every platform: "Antwerpen Centraal Station perron 2". The platform is shown separately.
    private val deLijnPlatform = Regex("""\s+perron\s+[A-Z]?\d+[a-z]?$""", RegexOption.IGNORE_CASE)

    // Feeds whose names never start with the town in capitals; their "UZ Gent", "ZNA Middelheim" are no towns.
    private val mixedCaseFeeds = listOf("be-sncb_", "be-stib_", "be-delijn_")

    override fun localStopName(name: String, stopId: String?): String {
        val withoutPlatform = name.replace(deLijnPlatform, "")
        // TEC, and feeds that copy its names (FlixBus: "CHARLEROI Gare Centrale").
        val tecStyle = stopId == null || mixedCaseFeeds.none { stopId.startsWith(it) }
        return if (tecStyle) tecStopName(withoutPlatform) else withoutPlatform
    }

    /** Stops of Belgian feeds abroad: TEC's buses to "AACHEN Bushof", De Lijn's to "Baarle-Nassau … perron 1". */
    override fun feedName(name: String): String = tecStopName(name.replace(deLijnPlatform, ""))

    /** Only domestic trains are "regional" in the SNCB data (IC, L, P); to Belgians they are just trains. */
    override fun modeLabel(mode: TransportMode): Int? = if (mode == TransportMode.REGIONAL) R.string.mode_train else null
}

/*
 * TEC writes the town of a stop in capitals, without accents: "LIEGE Gare des Guillemins",
 * "MONT-SUR-MARCHIENNE Place". As "Liège, Gare des Guillemins" it reads like the other
 * countries' "Town, Stop" names. Names entirely in capitals ("TOURNAI SNCB") are left alone:
 * the town cannot be told from the rest.
 */

internal fun tecStopName(name: String): String {
    val words = name.split(' ')
    val townWords = words.takeWhile(::isTownWord).size
    if (townWords == 0 || townWords == words.size) return name
    val rest = words.drop(townWords).joinToString(" ")
    if (rest.none { it.isLowerCase() }) return name
    return townName(words.take(townWords).joinToString(" ")) + ", " + rest
}

// A town word is in capitals and has a vowel: "SNCB", "CHU", "S.N.C.B." or "58" belong to the stop.
private fun isTownWord(word: String): Boolean =
    word.any { it.isLetter() } && word.none { it.isLowerCase() || it == '.' } && word.any { it in TOWN_VOWELS }

private const val TOWN_VOWELS = "AEIOUYÀÂÄÉÈÊËÎÏÔÖÙÛÜŒ"

private val townWord = Regex("""[\p{L}]+""")

/** "MONT-SUR-MARCHIENNE" → "Mont-sur-Marchienne", "BRAINE-L'ALLEUD" → "Braine-l'Alleud", "LIEGE" → "Liège". */
internal fun townName(capitals: String): String {
    var first = true
    return townWord.replace(capitals) { match ->
        val word = match.value
        val lower = word.lowercase()
        // "L'ALLEUD": the elided article is lower case, the word after it capitalised.
        val elided = match.range.last + 1 < capitals.length && capitals[match.range.last + 1] in "'’" && lower in setOf("l", "d")
        val spelled = when {
            !first && (elided || lower in townParticles) -> townParticles[lower] ?: lower
            else -> accented[word] ?: lower.replaceFirstChar { it.titlecase() }
        }
        first = false
        spelled
    }
}

private val townParticles = mapOf(
    "le" to "le", "la" to "la", "les" to "les", "de" to "de", "des" to "des", "du" to "du",
    "sur" to "sur", "sous" to "sous", "lez" to "lez", "au" to "au", "aux" to "aux", "en" to "en",
    "et" to "et", "a" to "à", "deux" to "deux",
)

/**
 * Words of Walloon place names that are written with accents. TEC's capitals drop them; this
 * restores those of the localities with the most stops (from the TEC timetable of November 2026).
 * Words missing here simply stay without accents.
 */
private val accented: Map<String, String> = listOf(
    "Achêne", "André", "Anhée", "Armentières", "Basècles", "Berzée", "Bièvre", "Biesmerée", "Bléharies",
    "Boëlhe", "Bossière", "Brûly", "Bruyère", "Bruyères", "Buissière", "Bütgenbach", "Carnières", "Cécile",
    "Céroux", "Chastrès", "Château", "Châtelet", "Châtelineau", "Châtillon", "Chaussée", "Chêne", "Chênée",
    "Chéoux", "Chératte", "Chièvres", "Cognelée", "Crisnée", "Denée", "Écaussinnes", "Éghezée", "Église",
    "Églises", "Érezée", "Évegnée", "Évêque", "Falaën", "Ferrières", "Flémalle", "Flénu", "Fléron",
    "Forrières", "Gelbressée", "Gérard", "Gérompont", "Gérouville", "Géry", "Gimnée", "Gomezée", "Gozée",
    "Grâce", "Grivegnée", "Hamipré", "Hannêche", "Harzé", "Hastière", "Havré", "Hennuyères", "Hérinnes",
    "Hermée", "Héron", "Hévillers", "Hozémont", "Keumiée", "Labuissière", "Lamontzée", "Léger", "Léglise",
    "Liège", "Louveigné", "Louvière", "Magnée", "Maisières", "Méan", "Médard", "Mélin", "Minière",
    "Moignelée", "Montegnée", "Morialmé", "Néchin", "Néthen", "Neufchâteau", "Othée", "Ougrée", "Pâturages",
    "Péronnes", "Péruwelz", "Piéton", "Piétrain", "Plombières", "Quévy", "Quiévrain", "Rabosée", "Rémy",
    "Rèves", "Rimière", "Rivière", "Romsée", "Ronquières", "Rosière", "Rosières", "Salmchâteau", "Sauvenière",
    "Strée", "Suarlée", "Sûre", "Thiméon", "Vodecée", "Vogenée", "Wagnelée", "Wanfercée", "Warzée", "Wépion",
    "Wihéries", "Visé",
).associateBy { word ->
    Normalizer.normalize(word, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").uppercase()
}
