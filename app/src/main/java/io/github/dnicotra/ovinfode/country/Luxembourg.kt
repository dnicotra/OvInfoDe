package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode

/**
 * Luxembourg: the national timetable of the Administration des transports publics (ATP), with
 * every CFL train, the Luxtram, the city buses of Luxembourg (AVL), the regional RGTR buses and
 * TICE. Stops are named "Town, Stop" ("Esch-sur-Alzette, Gare"), in the capital "Quarter, Stop"
 * ("Bonnevoie, Rotonde"), so destinations never need a town lookup.
 *
 * Transitous has no real-time data for Luxembourg: the ATP publishes it only through the
 * mobiliteit.lu API, which needs a personal key. Only trains that a neighbouring country's
 * feed also carries can come with real-time data (see [io.github.dnicotra.ovinfode.data.mergeSameTrains]).
 */
object Luxembourg : Country("lu") {
    override val outline = Outline(CountryOutlines.LU)

    override val hasRealtimeData = false

    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean = false

    // ATP stop ids are HAFAS station numbers whose first digit after "000" is the country: 1, 2
    // and 9 (provisional stops) Luxembourg, 3 Belgium, 4 France, 5 Germany. Unlike the border
    // outlines, they place Vianden, Echternach or Remich, right on the border rivers, correctly.
    private val hafasStation = Regex("""^000(\d)\d{8}$""")

    override fun countryInStopId(localId: String): String? = when (hafasStation.find(localId)?.groupValues?.get(1)) {
        "1", "2", "9" -> code
        "3" -> "be"
        "4" -> "fr"
        "5" -> "de"
        else -> null
    }

    /** CFL's IC, RE and RB are all regional trains in the data; in Luxembourg they are just trains. */
    override fun modeLabel(mode: TransportMode): Int? = if (mode == TransportMode.REGIONAL) R.string.mode_train else null
}
