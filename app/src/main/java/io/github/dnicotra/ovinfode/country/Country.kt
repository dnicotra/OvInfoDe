package io.github.dnicotra.ovinfode.country

import androidx.annotation.StringRes
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.shortStopName

/**
 * What the app needs to know about one country's public transport. Transitous delivers every
 * country's data in the same format, but each country's timetable feeds name their stops and
 * lines differently, and locals call things differently. These rules make departures read the
 * way people there expect.
 *
 * To support another country:
 * 1. add an object like [Netherlands] that overrides what differs from the defaults here,
 * 2. list it in [Countries.all],
 * 3. generate its outline: `python3 tools/country_outlines.py NL DE LU XX > …/country/CountryOutlines.kt`,
 * 4. add a `values-xx/strings.xml` translation if it speaks a new language.
 */
abstract class Country(
    /** ISO 3166-1 alpha-2 code in lower case, as in stop ids ("de:08111:…") and feed names ("nl-OpenOV"). */
    val code: String,
) {
    /** Land area, for stops whose id does not say where they are; null: recognised by id only. */
    internal open val outline: Outline? = null

    /** False when Transitous has no real-time data for this country's own transport (the board says so). */
    open val hasRealtimeData: Boolean = true

    /**
     * The country code that a stop id of this country's own feed encodes, for feeds whose ids say
     * where a stop is in a way of their own (Luxembourg's); null when the id does not say.
     * [localId] is the id without the feed name.
     */
    internal open fun countryInStopId(localId: String): String? = null

    /** Country-specific stop name fixes, applied before the general rules of [shortStopName]. */
    protected open fun localStopName(name: String): String = name

    /** The name to show for a stop in this country. */
    fun stopName(name: String): String = shortStopName(localStopName(name))

    /**
     * The line as people here know it, for trips of this country's timetable feeds
     * (e.g. "IC" for the Dutch "Intercity"). [tripId] is the Transitous trip id.
     */
    open fun line(line: Line, tripId: String): Line = line

    /** The part of an alert in [language], for feeds that put several languages into one text. */
    open fun alertText(text: String, language: String): String = text

    /**
     * True when [place], where a trip ends, may be named without its town, so that a
     * departure "to Hauptbahnhof" needs one. [contextStopId] is the stop the user is looking at.
     * Names written "Town, Stop" or "Town Hbf" already have it.
     */
    open fun needsTown(place: NamedPlace, contextStopId: String?): Boolean =
        !place.name.contains(',') && !townStation.matches(place.name)

    /** The local name of a transport mode, e.g. "U-Bahn" in Germany; null keeps the general name. */
    @StringRes
    open fun modeLabel(mode: TransportMode): Int? = null

    override fun toString(): String = code.ifEmpty { "other" }

    private companion object {
        // "Karlsruhe Hbf", "Homburg (Saar) Hbf", "Fulda Bf (tief)": a station named after its town.
        val townStation = Regex("""^.*\S (?:Hbf|Bf)(?: \(.*\))?$""")
    }
}

/** Any country without rules of its own: the defaults of [Country]. */
object OtherCountry : Country("")

object Countries {
    /** Countries with rules of their own. Where outlines overlap at a border, the first one wins. */
    val all: List<Country> = listOf(Netherlands, Germany, Luxembourg)

    fun byCode(code: String?): Country? = code?.let { c -> all.firstOrNull { it.code.equals(c, ignoreCase = true) } }

    /**
     * The country a stop lies in. Transitous stop ids are "<feed>_<id of the feed>", e.g.
     * "de-DELFI_de:08111:6115:2:3" or "nl-OpenOV_3980180". The feed says little (DELFI also
     * covers Dutch stations, an Austrian feed German ones), so this goes by:
     * 1. the code of a country in [all] at the start of the feed's id ("de:…", "NL:S:…"), which is exact,
     *    or the country that the id of the feed's own country encodes (Luxembourg's "000200405020"),
     * 2. the outlines of the countries in [all],
     * 3. the feed's country, for stops just outside its outline (coast, ferry piers), unless
     *    the id has a prefix like a country code.
     */
    fun of(stopId: String?, lat: Double, lon: Double): Country {
        val feed = stopId?.substringBefore('_', missingDelimiterValue = "").orEmpty()
        val local = stopId?.substringAfter('_').orEmpty()
        // Other two-letter prefixes ("fr:…" in DELFI, but also "gs:delijn:…") are no proof of anything.
        val localCode = localCountryCode.find(local)?.groupValues?.get(1)
        byCode(localCode)?.let { return it }
        val feedCountry = feedCountryCode.find(feed)?.let { byCode(it.groupValues[1]) }
        // Unlike a prefix, a feed's own id scheme is proof, also of a country without rules.
        if (localCode == null) feedCountry?.countryInStopId(local)?.let { return byCode(it) ?: OtherCountry }
        val point = LatLon(lat, lon)
        all.firstOrNull { it.outline?.contains(point) == true }?.let { return it }
        if (localCode == null && feedCountry?.outline?.isNear(point, km = NEAR_KM) == true) return feedCountry
        return OtherCountry
    }

    /** Country of the timetable feed a trip comes from ("20261126_09:27_nl-OpenOV_395288670"), if it has rules. */
    fun ofTrip(tripId: String?): Country? = tripId?.let { tripFeedCode.find(it) }?.let { byCode(it.groupValues[1]) }

    private const val NEAR_KM = 3.0
    private val localCountryCode = Regex("""^([A-Za-z]{2}):[A-Za-z0-9]""")
    private val feedCountryCode = Regex("""^([a-z]{2})-""")
    private val tripFeedCode = Regex("""^\d{8}_\d{1,2}:\d{2}_([a-z]{2})-""")
}

fun NamedPlace.country(): Country = Countries.of(id, lat, lon)

fun Stop.country(): Country = Countries.of(id, lat, lon)
