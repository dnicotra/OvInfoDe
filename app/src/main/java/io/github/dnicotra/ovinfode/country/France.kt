package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.frenchStopName

/**
 * France: SNCF's national timetable (TGV INOUI, OUIGO, Intercités, TER) with real-time data, the
 * Île-de-France Mobilités feed (Paris metro, RER, Transilien, trams and buses) and some two hundred
 * regional and city feeds (TCL in Lyon, Tisséo in Toulouse, RTM in Marseille, Naolib in Nantes…).
 * Destinations are towns or places of the same city, so no town lookups. See docs/research.md
 * for the recorded data behind these rules.
 */
object France : Country("fr") {
    override val outline = Outline(CountryOutlines.FR)

    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean = false

    override fun localStopName(name: String): String = frenchStopName(name)

    /** Stops of French feeds abroad: Soléa's buses to Basel, Lio's "TOWN - Stop" style. */
    override fun feedName(name: String): String = frenchStopName(name)

    private const val IDFM = "_fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_"

    // SNCF names trains by product and number: "TGV INOUI 6641", "OUIGO 7829", "TER 889652", "IC 4300".
    private val productAndNumber = Regex("""^(.+?) (\d{2,6})$""")

    /** SNCF products as on the departure boards in stations, and whether they are long-distance. */
    private val products: Map<String, Pair<String, Boolean>> = mapOf(
        "TGV INOUI" to ("TGV" to true),
        "TGV" to ("TGV" to true),
        "OUIGO" to ("OUIGO" to true),
        // OUIGO Train Classique: conventional trains, sold like OUIGO.
        "OUIGO_TC" to ("OUIGO" to true),
        "TGV Lyria" to ("Lyria" to true),
        "IC" to ("IC" to true),
        "ICN" to ("IC Nuit" to true),
        "ICE" to ("ICE" to true),
        "ES" to ("Eurostar" to true),
        "TER" to ("TER" to false),
    )

    // Paris: RER lines A to E, Transilien lines H to V (single letters too), metro lines "1" to "14", "3B", "7B".
    private val rerLines = setOf("A", "B", "C", "D", "E")
    private val transilienLine = Regex("""^[F-Z]$""")
    private val parisMetro = Regex("""^\d{1,2}(?:B|bis)?$""")

    override fun line(line: Line, tripId: String): Line {
        if (IDFM in tripId) return parisLine(line)
        if (!tripId.contains("_fr-")) return line
        val train = line.mode == TransportMode.LONG_DISTANCE || line.mode == TransportMode.REGIONAL || line.mode == TransportMode.SUBURBAN
        productAndNumber.matchEntire(line.name)?.let { match ->
            val (name, longDistance) = products[match.groupValues[1]] ?: return line
            // The number is shown with the trip: six-digit TER numbers do not fit a line badge.
            val mode = when {
                !train -> line.mode // TER coaches ("TER 44504" as a bus)
                longDistance -> TransportMode.LONG_DISTANCE
                else -> TransportMode.REGIONAL
            }
            return line.copy(name = name, mode = mode)
        }
        // TER BreizhGo names its trains after the route: "Paris - Chartres - Le Mans".
        if (train && " - " in line.name) return line.copy(name = "TER", mode = TransportMode.REGIONAL)
        return line
    }

    private fun parisLine(line: Line): Line = when {
        line.mode.isRail && line.mode != TransportMode.SUBWAY && line.name in rerLines ->
            line.copy(name = "RER ${line.name}", mode = TransportMode.SUBURBAN)
        line.mode.isRail && line.mode != TransportMode.SUBWAY && transilienLine.matches(line.name) ->
            line.copy(mode = TransportMode.SUBURBAN)
        line.mode == TransportMode.SUBWAY && parisMetro.matches(line.name) -> line.copy(name = "M${line.name.replace("bis", "B")}")
        else -> line
    }

    // RER and Transilien trains carry their mission code as headsign ("UZAR", "VOLA"), as on the
    // platform displays, the TER of Hauts-de-France their train number ("844051"); the final
    // stop's name says where they go.
    private val codeHeadsign = Regex("""^(?:[A-Z]{4}|\d{3,6})$""")

    override fun headsign(headsign: String, finalStop: String?): String =
        if (finalStop != null && codeHeadsign.matches(headsign)) finalStop else headsign

    override fun operatorName(name: String): String = if (name == "SNCF VOYAGEURS") "SNCF" else name

    // RER A trip numbers are mission codes with a counter ("UZAR03"); the code is what the displays show.
    private val missionWithCounter = Regex("""^([A-Z]{4})\d{2}$""")
    private val trainNumber = Regex("""^(\d{2,6})(?:-\d+)?$""")

    /**
     * Train numbers and RER missions; the metro's "RATP" and the trip numbers of buses and trams
     * (TCL: "REF~250426~181226~Itinéraire nominal complet") are no use to passengers.
     */
    override fun tripNumber(number: String, line: Line): String? {
        if (!line.mode.isRail && line.name != "TER") return null
        if (line.mode == TransportMode.SUBWAY) return null
        missionWithCounter.matchEntire(number)?.let { return it.groupValues[1] }
        return trainNumber.matchEntire(number)?.groupValues?.get(1)
    }

    /**
     * Trenitalia's Frecciarossa Paris–Lyon–Milan comes twice: from Trenitalia France, with
     * real-time data but without a train number ("FR" to "Paris-Gare-de-Lyon"), and from the
     * Italian timetable, as "FR 6648". Shown once: with the times of the French version and
     * the names of the Italian one.
     */
    override fun withoutDuplicates(departures: List<Departure>): List<Departure> {
        val french = departures.filter { TRENITALIA_FRANCE in it.tripId }
        if (french.isEmpty()) return departures
        val italian = departures.filter { "_it-trenitalia_" in it.tripId }
        val replaced = HashMap<Departure, Departure>()
        val dropped = HashSet<Departure>()
        french.forEach { fr ->
            val twin = italian.firstOrNull { it.scheduled == fr.scheduled && it.line.name.startsWith(fr.line.name) && it !in dropped } ?: return@forEach
            if (fr.isRealtime || !twin.isRealtime) {
                replaced[fr] = fr.copy(line = twin.line, tripNumber = twin.tripNumber, headsign = twin.headsign, destination = twin.destination)
                dropped += twin
            } else {
                dropped += fr
            }
        }
        return departures.mapNotNull { if (it in dropped) null else replaced[it] ?: it }
    }

    private const val TRENITALIA_FRANCE = "_fr-horaires-des-trains-trenitalia-france_"

    /** RER and Transilien are the only suburban trains in the French data. */
    override fun modeLabel(mode: TransportMode): Int? = if (mode == TransportMode.SUBURBAN) R.string.mode_suburban_fr else null
}
