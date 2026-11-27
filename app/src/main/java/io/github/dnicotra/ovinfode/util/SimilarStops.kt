package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import java.text.Normalizer

/*
 * Stops with similar names close together: the stops of one place that the timetables keep
 * apart. Paris Gare de Lyon is "Gare de Lyon" for the metro, RER and buses and "Paris Gare de
 * Lyon" for SNCF; Lyon Part-Dieu is "Lyon Part Dieu" for trains and "Gare Part-Dieu V.Merle"
 * for TCL; Berlin Alexanderplatz is "S+U Alexanderplatz" and "Alexanderplatz (Berlin) [Bus
 * Memhardstr.]". Each has departures of its own, so the board offers to switch between them.
 */

/** How far apart stops of one place can be. */
const val SIMILAR_STOP_METERS = 400

/** Words that say nothing about which place a stop is. */
private val fillerWords = setOf(
    "de", "du", "des", "la", "le", "les", "di", "del", "der", "die", "das", "am", "an", "im", "van", "het", "of", "the",
    "s", "u", "sncf", "rer", "bus", "tram", "metro", "gl", "voie", "quai", "perron", "spoor", "steig",
)

/** Words that a stop shares with every station: alone they make no two names similar. */
private val stationWords = setOf(
    "gare", "station", "stazione", "bahnhof", "bf", "hbf", "hauptbahnhof", "centraal", "centrale", "central", "zob",
)

private val wordPattern = Regex("""[\p{L}\d]+""")

/** The words of a name that tell places apart: lower case, without accents and filler words. */
internal fun significantWords(name: String): Set<String> =
    wordPattern.findAll(Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").lowercase())
        .map { it.value }
        .filter { it.length >= 2 && it !in fillerWords }
        .toSet()

/**
 * True when [a] and [b] name the same place: they share a word that is not just "station", and
 * most words of the shorter name are in the longer one ("Gare de Lyon" / "Paris Gare de Lyon",
 * "Lyon Part Dieu" / "Gare Part-Dieu V.Merle", "Köln Hbf" / "Köln Dom/Hbf", but not "Köln Hbf" /
 * "Köln Neumarkt").
 */
fun similarStopNames(a: String, b: String): Boolean {
    val wa = significantWords(a)
    val wb = significantWords(b)
    if (wa.isEmpty() || wb.isEmpty()) return false
    val common = wa intersect wb
    if (common.none { it.length >= 3 && it !in stationWords }) return false
    return common.size * 3 >= minOf(wa.size, wb.size) * 2
}

/**
 * The stops among [candidates] that belong to the same place as [stop]: close by, with a similar
 * name, closest first. Other parts of [stop] itself (the same name a few steps away) are left out,
 * and of several stops of one name the closest is kept.
 */
fun similarStopsNearby(stop: Stop, candidates: List<Stop>, maxMeters: Int = SIMILAR_STOP_METERS): List<NearbyStop> =
    candidates
        .asSequence()
        .filter { it.id != stop.id }
        .map { NearbyStop(it, distanceMeters(stop.location, it.location).toInt()) }
        .filter { it.distanceMeters <= maxMeters && similarStopNames(stop.name, it.stop.name) }
        .filterNot { it.stop.name == stop.name && it.distanceMeters < SAME_STOP_NAME_METERS }
        .sortedBy { it.distanceMeters }
        .distinctBy { it.stop.name }
        .toList()

private const val SAME_STOP_NAME_METERS = 60
