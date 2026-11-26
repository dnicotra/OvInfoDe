package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.italianCase
import io.github.dnicotra.ovinfode.util.italianStopName

/**
 * Italy: some forty regional and city feeds (most of them via BusOne) plus Trenitalia's national
 * timetable. They write names in capitals ("ROMA TERMINI") or all in lower case, which
 * [italianStopName] turns into the usual spelling, also for their stops abroad. Destinations are
 * towns, stations named after them or streets of the same city, so no town lookups.
 */
object Italy : Country("it") {
    override val outline = Outline(CountryOutlines.IT)

    // The Natural Earth outline has no Procida (EAV buses) or Capri, 4 and 8 km off the coast.
    override val nearKm = 6.0

    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean = false

    override fun localStopName(name: String): String = italianStopName(name)

    override fun feedName(name: String): String = italianStopName(name)

    /**
     * Milan's metro lines in the colours of the ATM network map. The feed has plain ff0000,
     * 73ff01 and fcff01 with black text, hard to read and not what the signs look like.
     */
    val MILAN_METRO_COLORS: Map<String, Int> = mapOf(
        "M1" to 0xFFEE2E26.toInt(),
        "M2" to 0xFF5E9632.toInt(),
        "M3" to 0xFFFFBA14.toInt(),
        "M4" to 0xFF001588.toInt(),
        "M5" to 0xFF8584BB.toInt(),
    )

    // Rome's metro lines are "MEA", "MEB", "MEB1", "MEC" in the feed; on signs they are A, B, B1, C.
    private val romeMetro = Regex("""^ME([A-D]\d?)$""")

    // Trenitalia names trains by category and number: "FR 9503", "REG 12655".
    private val categoryAndNumber = Regex("""^([A-Z]{1,4}) \d+$""")

    override fun line(line: Line, tripId: String): Line = when {
        // Regional trains are told apart by their category; their five-digit numbers ("REG 12655")
        // do not fit a line badge and are shown with the trip. Long-distance trains keep their
        // number, which is on the ticket, like "ICE 1697" in Germany.
        "_it-trenitalia_" in tripId && (line.mode == TransportMode.REGIONAL || line.mode == TransportMode.SUBURBAN) ->
            categoryAndNumber.matchEntire(line.name)?.let { line.copy(name = it.groupValues[1]) } ?: line
        line.mode != TransportMode.SUBWAY -> line
        // ATM numbers its metro lines "1" to "5"; everyone says M1 to M5.
        "_it-Lombardia-ATM_" in tripId && line.name.length == 1 && line.name[0] in '1'..'5' -> {
            val name = "M${line.name}"
            line.copy(name = name, color = MILAN_METRO_COLORS.getValue(name), textColor = if (name == "M3") BLACK else WHITE)
        }
        "_it-Lazio-Rome_" in tripId -> romeMetro.matchEntire(line.name)?.let { line.copy(name = it.groupValues[1]) } ?: line
        else -> line
    }

    /** "TRENITALIA" → "Trenitalia"; short names in capitals ("ATM", "GTT") are abbreviations. */
    override fun operatorName(name: String): String =
        if (name.length >= 6 && name.none { it.isLowerCase() }) italianCase(name) else name

    // Trenord writes the line into the train number: "S3 - 823", "RE_80 - 25510".
    private val numberAfterLine = Regex("""^\S+ - (\d+)$""")

    /** Train numbers are useful; the trip numbers of buses, trams and ferries are internal ids (ATAC: "27333574"). */
    override fun tripNumber(number: String, line: Line): String? {
        if (!line.mode.isRail) return null
        return numberAfterLine.matchEntire(number)?.groupValues?.get(1) ?: number
    }

    // Trenitalia's trip ids say which version of the timetable a train is from:
    // "…_it-trenitalia_0083_680083_0_v_68-9490-5B67-0083_…", "p" or "v".
    private val trenitaliaVersion = Regex("""_it-trenitalia_[^_]+_[^_]+_\d+_([pv])_""")

    /**
     * Trenitalia's timetable has many trains twice, as version "p" and "v", sometimes with other
     * times or categories (REG 4594 and RV 4594 to Fiumicino Aeroporto, FR 9308 at 08:20 and 08:25
     * on 2026-11-27). The "p" copies are the outdated ones (REG 4594 runs back and forth all day),
     * so they go when the same train is on the board as "v".
     */
    override fun withoutDuplicates(departures: List<Departure>): List<Departure> {
        fun Departure.version() = trenitaliaVersion.find(tripId)?.groupValues?.get(1)
        val current = departures.filter { it.version() == "v" }.mapNotNull { it.tripNumber }.toSet()
        if (current.isEmpty()) return departures
        return departures.filterNot { it.version() == "p" && it.tripNumber in current }
    }

    // Roma Mobilità signs every alert "(@romamobilita)".
    private val signature = Regex("""\s*\(@\w+\)""")

    override fun alertText(text: String, language: String): String = signature.replace(text, "")

    private val BLACK = 0xFF000000.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
}
