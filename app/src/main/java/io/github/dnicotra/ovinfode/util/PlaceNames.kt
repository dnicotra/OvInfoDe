package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.data.model.NamedPlace
import java.util.Locale

/*
 * Helpers for adding the town to stop names that lack it (see Country.needsTown, which
 * decides when that is needed, country by country).
 */

// German stop ids (DHID) look like "de:08111:6115:2:3": country, district key, station, platform.
// Transitous prefixes them with the feed name, e.g. "de-DELFI_de:08111:6115:2:3".
private val dhid = Regex("""(?:^|[^a-z])de:(\d{5}):([^:_]+)""")

/** District key (Kreisschlüssel) of a German stop id, e.g. "08111" for Stuttgart. */
fun districtOf(stopId: String?): String? = stopId?.let { dhid.find(it)?.groupValues?.get(1) }

/** Cache key for a place: the station part of a German stop id (shared by all its platforms). */
fun placeKey(place: NamedPlace): String =
    place.id?.let { id -> dhid.find(id)?.let { "de:${it.groupValues[1]}:${it.groupValues[2]}" } ?: id }
        ?: String.format(Locale.ROOT, "%.4f,%.4f", place.lat, place.lon)

/** "Hauptbahnhof" + "Stuttgart" → "Stuttgart, Hauptbahnhof"; names that already mention the town are kept. */
fun qualifyName(name: String, town: String?): String {
    if (town.isNullOrBlank()) return name
    // "Frankfurt am Main" is written "Frankfurt (Main)" in stop names: compare the first word.
    val firstWord = town.split(' ', '-', '(', '/').first { it.isNotBlank() }
    val key = (if (firstWord.length >= 4) firstWord else town).lowercase()
    return if (name.lowercase().contains(key)) name else "$town, $name"
}
