package io.github.dnicotra.ovinfode.country

import androidx.annotation.StringRes
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.repairEncoding
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
 * 3. generate its outline: `python3 tools/country_outlines.py NL DE IT LU BE BE-VLG BE-BRU FR XX > …/country/CountryOutlines.kt`,
 * 4. add a `values-xx/strings.xml` translation if it speaks a new language.
 */
abstract class Country(
    /** ISO 3166-1 alpha-2 code in lower case, as in stop ids ("de:08111:…") and feed names ("nl-OpenOV"). */
    val code: String,
) {
    /** Land area, for stops whose id does not say where they are; null: recognised by id only. */
    internal open val outline: Outline? = null

    /** How far outside [outline] stops of this country's feeds still belong to it (piers, islands the outline lacks). */
    internal open val nearKm: Double = 3.0

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

    /**
     * Like [localStopName], for countries whose feeds each name their stops their own way:
     * [stopId], the Transitous id of the stop if known, tells which feed it comes from.
     */
    protected open fun localStopName(name: String, stopId: String?): String = localStopName(name)

    /**
     * Fixes for names as this country's timetable feeds write them, for stops of these feeds
     * abroad (e.g. Italian feeds write "ZURIGO HB" in capitals). Usually part of [localStopName].
     */
    protected open fun feedName(name: String): String = name

    /**
     * The name to show for a stop in this country; [feed] is the country of the timetable feed it
     * comes from and [stopId] its Transitous id, if known.
     */
    fun stopName(name: String, feed: Country? = null, stopId: String? = null): String {
        val repaired = repairEncoding(name)
        return shortStopName(localStopName(if (feed != null && feed !== this) feed.feedName(repaired) else repaired, stopId))
    }

    /** The destination to show for a trip of this country's feeds, given its [headsign] and the name of its [finalStop]. */
    open fun headsign(headsign: String, finalStop: String?): String = headsign

    /**
     * The language to ask the API for when showing places [at] this position (null: not known)
     * to someone who reads [userLanguage]. Usually that language; countries whose feeds only
     * translate well into some languages pick the local one instead.
     */
    open fun requestLanguage(userLanguage: String, at: LatLon?): String = userLanguage

    /**
     * The line as people here know it, for trips of this country's timetable feeds
     * (e.g. "IC" for the Dutch "Intercity"). [tripId] is the Transitous trip id.
     */
    open fun line(line: Line, tripId: String): Line = line

    /** The name to show for an operator of this country's feeds. */
    open fun operatorName(name: String): String = name

    /** The trip number to show for a trip of this country's feeds ([number] is its GTFS trip short name), or null for none. */
    open fun tripNumber(number: String, line: Line): String? = number

    /**
     * [departures] of one board without those of this country's feeds that only repeat another
     * one (e.g. an outdated copy of a train). Applied before departures with the same line,
     * destination and time are merged.
     */
    open fun withoutDuplicates(departures: List<Departure>): List<Departure> = departures

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
    /**
     * Countries with rules of their own. Where outlines overlap at a border, the country of the
     * stop's feed wins, otherwise the first one.
     */
    val all: List<Country> = listOf(Netherlands, Germany, Italy, Luxembourg, Belgium, France)

    fun byCode(code: String?): Country? = code?.let { c -> all.firstOrNull { it.code.equals(c, ignoreCase = true) } }

    /**
     * The country a stop lies in. Transitous stop ids are "<feed>_<id of the feed>", e.g.
     * "de-DELFI_de:08111:6115:2:3" or "nl-OpenOV_3980180". The feed says little (DELFI also
     * covers Dutch stations, an Austrian feed German ones), so this goes by:
     * 1. the code of a country in [all] at the start of the feed's id ("de:…", "NL:S:…"), which is exact,
     *    or the country that the id of the feed's own country encodes (Luxembourg's "000200405020"),
     * 2. the outlines of the countries in [all] (at borders, where they overlap, the feed's country first),
     * 3. the feed's country, for stops just outside its outline (coast, ferry piers), unless
     *    the id has a prefix like a country code.
     */
    fun of(stopId: String?, lat: Double, lon: Double): Country {
        val feed = stopId?.substringBefore('_', missingDelimiterValue = "").orEmpty()
        val local = stopId?.substringAfter('_').orEmpty()
        // Other country codes ("fr:…" in DELFI) are no proof of anything.
        val localCode = localCountryCode.find(local)?.groupValues?.get(1)
        byCode(localCode)?.let { return it }
        val feedCountry = feedCountryCode.find(feed)?.let { byCode(it.groupValues[1]) }
        // Unlike a prefix, a feed's own id scheme is proof, also of a country without rules.
        if (localCode == null) feedCountry?.countryInStopId(local)?.let { return byCode(it) ?: OtherCountry }
        val point = LatLon(lat, lon)
        val inside = all.filter { it.outline?.contains(point) == true }
        (inside.firstOrNull { it == feedCountry } ?: inside.firstOrNull())?.let { return it }
        if (localCode == null && feedCountry != null && feedCountry.outline?.isNear(point, km = feedCountry.nearKm) == true) return feedCountry
        return OtherCountry
    }

    /** The country at a position (for places without a stop id). */
    fun at(point: LatLon): Country = of(null, point.lat, point.lon)

    /** Country of the timetable feed a stop comes from ("it-trenitalia_01700:station"), if it has rules. */
    fun ofFeed(stopId: String?): Country? =
        stopId?.substringBefore('_', missingDelimiterValue = "")?.let { feedCountryCode.find(it) }?.let { byCode(it.groupValues[1]) }

    /** Country of the timetable feed a trip comes from ("20261126_09:27_nl-OpenOV_395288670"), if it has rules. */
    fun ofTrip(tripId: String?): Country? = tripId?.let { tripFeedCode.find(it) }?.let { byCode(it.groupValues[1]) }

    // "gs:delijn:…" and "gs:tec:…" (Belgian feeds) are GTFS stop namespaces, not country codes.
    private val localCountryCode = Regex("""^(?!gs:)([A-Za-z]{2}):[A-Za-z0-9]""")
    private val feedCountryCode = Regex("""^([a-z]{2})-""")
    private val tripFeedCode = Regex("""^\d{8}_\d{1,2}:\d{2}_([a-z]{2})-""")
}

fun NamedPlace.country(): Country = Countries.of(id, lat, lon)

fun Stop.country(): Country = Countries.of(id, lat, lon)
