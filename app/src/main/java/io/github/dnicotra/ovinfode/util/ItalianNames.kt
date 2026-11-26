package io.github.dnicotra.ovinfode.util

/*
 * Name rules for Italian timetable feeds (see Italy in country/). Most of them write stop names
 * in capitals, as on old departure boards ("ROMA TERMINI", "P.ZA STAZIONE S. PIETRO (FL)"), ATM
 * in Milan writes them all in lower case ("p.le negrelli"), and some add codes for their own
 * use. These rules give the usual Italian spelling: "Roma Termini", "P.za Stazione S. Pietro (FL)",
 * "P.le Negrelli". Names already written with care ("Milano Cadorna", "Bologna C.le/AV") are
 * left alone, apart from words in capitals that are not abbreviations.
 */

/** A stop name from an Italian feed, as Italians write it. Idempotent. */
fun italianStopName(name: String): String {
    var n = cotralCode.replace(name, "").replace(" | ", ", ")
    gttStop.matchEntire(n)?.let { n = it.groupValues[1] }
    n = spaces.replace(n.trim().trimEnd('/').trim(), " ")
    n = italianCase(n)
    staStation.matchEntire(n)?.let { m ->
        val (town, rest) = m.destructured
        n = if (rest.isEmpty()) "$town, Stazione" else "$town $rest"
    }
    return n
}

// Cotral (Lazio): "ROMA | Termini FS (Giolitti) # f14637" – the stop code goes, the bar becomes a comma.
private val cotralCode = Regex("""\s+#\s*\w+$""")

// GTT (Turin): "Fermata 252 - PORTA NUOVA", destinations "Fermata 3456 - STATI UNITI CAP" (capolinea, the terminus).
private val gttStop = Regex("""^Fermata \d+[A-Za-z]? - (.+?)(?: CAP)?$""")

private val spaces = Regex("""\s{2,}""")

// STA (South Tyrol) names stations "Roma, Stazione di Roma Termini" and "Bolzano, Stazione di Bolzano".
private val staStation = Regex("""^([^,]+), Stazione di \1(?: (.+))?$""")

/**
 * Upper and lower case the Italian way: words capitalised, prepositions in lower case
 * ("Reggio di Calabria Centrale"), abbreviations and codes kept ("FS", "M3", "XX Settembre",
 * "(MA)"), an apostrophe for a missing accent turned back into it ("CITTA'" → "Città").
 * Only names in capitals or all in lower case are recased completely; in other names, only
 * words in capitals that are not abbreviations.
 */
fun italianCase(name: String): String {
    val hasUpper = name.any { it.isUpperCase() }
    val hasLower = name.any { it.isLowerCase() }
    if (!hasUpper && !hasLower) return name
    val uniform = !(hasUpper && hasLower)
    val out = StringBuilder(name.length + 4)
    var last = 0
    var parens = 0
    var phraseStart = true
    for (match in token.findAll(name)) {
        val separator = name.substring(last, match.range.first)
        out.append(separator)
        parens += separator.count { it == '(' } - separator.count { it == ')' }
        if (separator.any { it in PHRASE_SEPARATORS }) phraseStart = true
        out.append(caseToken(match.value, uniform, phraseStart, parens > 0))
        phraseStart = false
        last = match.range.last + 1
    }
    out.append(name, last, name.length)
    return out.toString()
}

private val token = Regex("""[\p{L}\p{N}'’.]+""")
private const val PHRASE_SEPARATORS = "(-/,|\":;"
private const val VOWELS = "aeiouyàèéìíòóùú"

// Prepositions and the conjunction "e" are written in lower case inside names; articles are not
// in this list, because inside names they mostly belong to a surname ("Via La Marmora").
private val minorWords = setOf(
    "di", "del", "dello", "della", "dei", "degli", "delle",
    "da", "dal", "dallo", "dalla", "dai", "dagli", "dalle",
    "a", "ad", "al", "allo", "alla", "ai", "agli", "alle",
    "in", "nel", "nello", "nella", "nei", "negli", "nelle",
    "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle",
    "con", "col", "per", "tra", "fra", "e", "ed",
)

// Elided prepositions: "dell'Arco", "d'Armi"; other elisions are capitalised ("Sant'Angelo", "L'Aquila").
private val elidedMinorWords = setOf("d", "dell", "dall", "all", "nell", "sull", "coll")

// Abbreviations with vowels that are written in capitals.
private val acronyms = setOf(
    "av", "eur", "atm", "atac", "amt", "anm", "eav", "tper", "actv", "amat", "ataf", "atv",
    "asl", "inps", "inail", "cnr", "enel", "eni", "ikea", "aci", "uni",
)

// The endings of shortened words: "P.za" (piazza), "P.le" (piazzale), "V.le", "L.go", "C.so", "P.te",
// "Q.re" (quartiere), "Staz.ne", "C.le" (centrale), "F.lli" (fratelli), "C.da" (contrada), "V.lo" (vicolo),
// "SS.ma" (santissima).
private val abbreviationEndings = setOf(
    "le", "za", "zza", "zzo", "ta", "te", "go", "so", "ne", "re", "ro", "lli", "lo", "da", "co", "ni", "na", "ma", "mo",
)

private val roman = Regex("""^(?=[ivx])x{0,3}(?:ix|iv|v?i{0,3})$""")

// Line and platform codes: "M2", "N25", "T2", "MB1".
private val codeWithDigits = Regex("""^\p{L}{1,3}\d+\p{L}?$""")

private const val ACCENTED = "aàeèiìoòuù"

private fun caseToken(token: String, uniform: Boolean, first: Boolean, inParens: Boolean): String {
    val letters = token.filter { it.isLetter() }
    if (letters.isEmpty()) return token
    val upper = letters.all { it.isUpperCase() }
    val lower = letters.all { it.isLowerCase() }
    if (!upper && !lower) return token
    val lowered = token.lowercase()
    if (!uniform) {
        // In a name written with care, only words in capitals stand out, and short ones are abbreviations ("NOI", "AV").
        if (!upper || letters.length < 4 || lowered in acronyms || letters.none { it.lowercaseChar() in VOWELS } || roman.matches(lowered)) return token
    }
    if (token.any { it.isDigit() }) return if (codeWithDigits.matches(token)) token.uppercase() else token
    if ('.' in token) return dotted(lowered)
    if ('\'' in token || '’' in token) return elided(lowered, first)
    if (roman.matches(lowered)) return token.uppercase()
    // Province codes and metro lines in brackets: "(AL)", "(MA)", "(FL)".
    if (upper && inParens && letters.length <= 2) return token
    if (!first && lowered in minorWords) return lowered
    if (lowered in acronyms || letters.none { it.lowercaseChar() in VOWELS }) return token.uppercase()
    return capitalize(lowered)
}

/** "p.za" → "P.za", "staz.ne" → "Staz.ne", "s.maria" → "S. Maria", "s.m.e." → "S.M.E.", "loc." → "Loc.". */
private fun dotted(word: String): String {
    val parts = word.split('.')
    if (parts.all { it.length <= 1 }) return word.uppercase()
    if (parts.size == 2 && parts[1] in abbreviationEndings) return capitalize(parts[0]) + "." + parts[1]
    return buildString {
        parts.forEachIndexed { i, part ->
            if (i > 0) append(if (part.length >= 2) ". " else ".")
            append(if (part.length == 1) part.uppercase() else capitalize(part))
        }
    }
}

/** "dell'arco" → "dell'Arco", "sant'angelo" → "Sant'Angelo", "citta'" → "Città". */
private fun elided(word: String, first: Boolean): String {
    val apostrophe = if ('’' in word) '’' else '\''
    val parts = word.split('\'', '’')
    // Without accented capitals, a final apostrophe stands for the accent: "CITTA'", "CEFALU'", "VARE'".
    if (parts.size == 2 && parts[1].isEmpty() && parts[0].length >= 2) {
        val vowel = ACCENTED.indexOf(parts[0].last())
        if (vowel >= 0 && vowel % 2 == 0) return capitalize(parts[0].dropLast(1) + ACCENTED[vowel + 1])
    }
    return parts.mapIndexed { i, part ->
        when {
            i == 0 && !first && part in elidedMinorWords -> part
            else -> capitalize(part)
        }
    }.joinToString(apostrophe.toString())
}

private fun capitalize(word: String): String = word.replaceFirstChar { it.titlecase() }
