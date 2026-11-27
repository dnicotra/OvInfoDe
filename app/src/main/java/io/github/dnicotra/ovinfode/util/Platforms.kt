package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.TransportMode

/**
 * One place within a stop where vehicles leave from: a platform, a bus bay or a whole station
 * whose platforms the data does not place separately. German stations have every platform
 * of their U-Bahn, tram and bus stops at its own position ("Alexanderplatz, Gleis 1 (U2)");
 * many train stations have all their tracks at one point.
 */
data class PlatformPlace(
    val location: LatLon,
    /** The stop's name there, as the departures name it. */
    val stopName: String,
    /** Platform names ("2", "4 A-C"), in natural order; empty when the data has none. */
    val platforms: List<String>,
    /** Lines leaving from here, most frequent first. */
    val lines: List<Line>,
) {
    val mode: TransportMode get() = lines.firstOrNull()?.mode ?: TransportMode.OTHER
}

/** Positions closer than this are one place (the same platform from two feeds). */
private const val SAME_PLACE_METERS = 5.0

/**
 * The places [departures] leave from, ordered by mode, line and platform. Departures the data does not
 * locate are left out.
 */
fun platformPlaces(departures: List<Departure>): List<PlatformPlace> {
    val located = departures.filter { it.stopLocation != null }
    val groups = mutableListOf<MutableList<Departure>>()
    for (departure in located) {
        val group = groups.firstOrNull { distanceMeters(it.first().stopLocation!!, departure.stopLocation!!) < SAME_PLACE_METERS }
        if (group != null) group += departure else groups += mutableListOf(departure)
    }
    return groups.map { group ->
        PlatformPlace(
            location = group.first().stopLocation!!,
            stopName = group.groupingBy { it.stopName }.eachCount().maxBy { it.value }.key,
            platforms = group.mapNotNull { it.platform }.distinct().sortedWith(naturalOrder),
            lines = group.groupBy { it.line.name }.values.sortedByDescending { it.size }.map { it.first().line },
        )
    }.sortedWith(
        compareBy<PlatformPlace> { it.mode }
            .thenBy { it.lines.firstOrNull()?.name.orEmpty() }
            .thenBy(nullsLast(naturalOrder)) { it.platforms.firstOrNull() },
    )
}

// "2" before "10", "4 A-C" after "4": numbers by value, the rest by text.
private val number = Regex("""^\d+""")
private val naturalOrder: Comparator<String> = compareBy<String> { number.find(it)?.value?.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it }
