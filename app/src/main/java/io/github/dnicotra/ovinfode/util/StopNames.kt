package io.github.dnicotra.ovinfode.util

/*
 * Standard German abbreviations for stop names, as used by DB and on departure displays:
 * "Köln Hauptbahnhof" → "Köln Hbf", "Hermannstraße" → "Hermannstr.".
 * Words are only replaced as a whole (or as the end of a compound for "-straße"), so
 * "Bahnhofstraße" becomes "Bahnhofstr.", never "Bfstr.".
 */

private const val NOT_AFTER_LETTER_OR_HYPHEN = """(?<![\p{L}\-])"""
private const val NOT_BEFORE_LETTER = """(?!\p{L})"""

private fun word(w: String) = Regex(NOT_AFTER_LETTER_OR_HYPHEN + w + NOT_BEFORE_LETTER)

private val rules: List<Pair<Regex, String>> = listOf(
    word("Zentraler (?:Omnibus|Bus)bahnhof") to "ZOB",
    word("Hauptbahnhof") to "Hbf",
    word("Fernbahnhof") to "Fernbf",
    word("Regionalbahnhof") to "Regionalbf",
    word("Bahnhof") to "Bf",
    // "Hermannstraße", "Karl-Marx-Straße", "Kölner Straße"; also the Swiss/ASCII "strasse".
    Regex("""(?<=\p{L})stra(?:ß|ss)e$NOT_BEFORE_LETTER""") to "str.",
    Regex("""(?<!\p{L})Stra(?:ß|ss)e$NOT_BEFORE_LETTER""") to "Str.",
    Regex("""(?<!\p{L})Sankt(?= )""") to "St.",
    // "Mannheim, Hbf" → "Mannheim Hbf", the familiar form.
    Regex(""", (Hbf|Bf)(?=$| \()""") to " $1",
)

fun shortStopName(name: String): String =
    rules.fold(name) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
