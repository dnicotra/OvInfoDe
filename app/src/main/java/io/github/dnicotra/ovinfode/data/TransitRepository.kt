package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.country.Countries
import io.github.dnicotra.ovinfode.country.Country
import io.github.dnicotra.ovinfode.country.country
import io.github.dnicotra.ovinfode.data.api.ApiException
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.StopDepartures
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.util.boundingBox
import io.github.dnicotra.ovinfode.util.distanceMeters
import io.github.dnicotra.ovinfode.util.placeKey
import io.github.dnicotra.ovinfode.util.qualifyName
import io.github.dnicotra.ovinfode.util.SIMILAR_STOP_METERS
import io.github.dnicotra.ovinfode.util.similarStopsNearby
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class TransitRepository(
    private val api: TransitousApi,
    private val towns: TownResolver = TownResolver(api),
    /** The language the user reads (for tests; the app follows the device). */
    private val userLanguage: () -> String = TransitousApi::defaultLanguage,
) {
    /**
     * Where the departure boards were that listed a stop id: trips opened from a board are
     * asked for in that board's language (see [Country.requestLanguage]).
     */
    private val boardLocations = ConcurrentHashMap<String, LatLon>()

    /** The language to ask for places [at] this position, in country [country]. */
    private fun languageAt(at: LatLon, country: Country = Countries.at(at)): String =
        country.requestLanguage(userLanguage(), at)

    /**
     * Stops matching [query]. The geocoder answers with the name in the language that matched,
     * so the user's language is fine everywhere. Stops of one name a few steps apart
     * (platforms of De Lijn, the same station in two feeds) share their departures: kept once.
     */
    suspend fun searchStops(query: String, near: LatLon?): List<Stop> =
        api.geocode(query, near, language = userLanguage())
            .filter { it.type == "STOP" }
            .map { it.toStop() }
            .distinctBy { it.id }
            .fold(mutableListOf()) { kept, stop ->
                if (kept.none { it.name == stop.name && distanceMeters(it.location, stop.location) < SAME_STOP_METERS }) kept += stop
                kept
            }

    /**
     * Stops around [center], closest first. Starts with a small radius and widens it in
     * rural areas until a few stops are found.
     */
    suspend fun nearbyStops(center: LatLon, limit: Int = 15): List<NearbyStop> {
        var radius = 500
        var result: List<NearbyStop> = emptyList()
        while (radius <= 4000) {
            val (min, max) = boundingBox(center, radius)
            result = api.mapStops(min, max, language = languageAt(center))
                .filter { it.stopId != null }
                .map { place -> NearbyStop(place.toStop(), distanceMeters(center, LatLon(place.lat, place.lon)).toInt()) }
                .filter { it.distanceMeters <= radius }
                // Platforms of one station often come back separately: keep the closest.
                .groupBy { it.stop.name }
                .map { (_, sameName) -> sameName.minBy { it.distanceMeters } }
                .sortedBy { it.distanceMeters }
            if (result.size >= 5) break
            radius *= 2
        }
        return result.take(limit)
    }

    suspend fun departures(stop: Stop, count: Int, pageCursor: String? = null): StopDepartures {
        val language = languageAt(stop.location, stop.country())
        val result = api.stopTimes(stop.id, stop.location, count, pageCursor, language).toStopDepartures(userLanguage())
        if (boardLocations.size > MAX_BOARD_LOCATIONS) boardLocations.clear()
        boardLocations[stop.id] = stop.location
        result.departures.forEach { departure -> departure.stopId?.let { boardLocations[it] = stop.location } }
        val board = result.copy(departures = withTowns(result.departures, contextStopId = stop.id))
        if (pageCursor == null) synchronized(recentBoards) { recentBoards[stop.id] = board }
        return board
    }

    /** Recently loaded first pages of departure boards, by stop id: the map shows their platforms without asking again. */
    private val recentBoards = object : LinkedHashMap<String, StopDepartures>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StopDepartures>) = size > MAX_RECENT_BOARDS
    }

    /** The departures of [stop] for its map: the board loaded last, else asked for now. */
    suspend fun departuresForMap(stop: Stop): StopDepartures =
        synchronized(recentBoards) { recentBoards[stop.id] } ?: departures(stop, MAP_DEPARTURES)

    private val similarStopsCache = ConcurrentHashMap<String, List<NearbyStop>>()

    /**
     * Stops of the same place as [stop] that the timetables keep apart: close by and with a
     * similar name ("Gare de Lyon" for the Paris metro and RER, "Paris Gare de Lyon" for SNCF).
     * See [similarStopsNearby].
     */
    suspend fun similarStops(stop: Stop): List<NearbyStop> {
        similarStopsCache[stop.id]?.let { return it }
        val (min, max) = boundingBox(stop.location, SIMILAR_STOP_METERS)
        val candidates = api.mapStops(min, max, language = languageAt(stop.location, stop.country()))
            .filter { it.stopId != null }
            .map { it.toStop() }
        val result = similarStopsNearby(stop, candidates)
        if (similarStopsCache.size > MAX_BOARD_LOCATIONS) similarStopsCache.clear()
        similarStopsCache[stop.id] = result
        return result
    }

    /** A trip with all its stops; [contextStopId] is the stop the user came from. */
    suspend fun trip(tripId: String, contextStopId: String? = null): Trip {
        // The trip's feed decides; without a board to go by (e.g. after the app was restarted), the country's default.
        val board = contextStopId?.let { boardLocations[it] }
        val country = Countries.ofTrip(tripId) ?: board?.let { Countries.at(it) }
        val language = country?.requestLanguage(userLanguage(), board) ?: userLanguage()
        val trip = api.trip(tripId, language).toTrip(tripId, userLanguage())
        val context = contextStopId ?: trip.stops.firstOrNull()?.id
        val candidates = trip.stops.map { it.place }.filter { it.country().needsTown(it, context) }
        if (candidates.isEmpty()) return trip
        val known = towns.towns(candidates)
        if (known.isEmpty()) return trip
        val stops = trip.stops.map { stop ->
            known[placeKey(stop.place)]?.let { town -> stop.copy(name = stop.place.country().stopName(qualifyName(stop.name, town), Countries.ofFeed(stop.id), stop.id)) } ?: stop
        }
        val headsign = if (trip.headsign == trip.stops.lastOrNull()?.name) stops.last().name else trip.headsign
        return trip.copy(stops = stops, headsign = headsign)
    }

    /** Adds the town to destinations like "Hauptbahnhof" that lie elsewhere (see [io.github.dnicotra.ovinfode.country.Country.needsTown]). */
    private suspend fun withTowns(departures: List<Departure>, contextStopId: String?): List<Departure> {
        // Only when the headsign is the bare name of the final stop; other headsigns are written by the operator.
        fun Departure.candidate(): NamedPlace? = destination?.takeIf { headsign == it.name && it.country().needsTown(it, contextStopId) }
        val candidates = departures.mapNotNull { it.candidate() }
        if (candidates.isEmpty()) return departures
        val known = towns.towns(candidates)
        if (known.isEmpty()) return departures
        return departures.map { departure ->
            val destination = departure.candidate() ?: return@map departure
            val town = known[placeKey(destination)] ?: return@map departure
            val name = destination.country().stopName(qualifyName(departure.headsign, town), Countries.ofTrip(departure.tripId), destination.id)
            departure.copy(headsign = name, destination = departure.destination?.copy(name = name))
        }
    }
}

private const val MAX_BOARD_LOCATIONS = 2000
private const val SAME_STOP_METERS = 300
private const val MAX_RECENT_BOARDS = 8
private const val MAP_DEPARTURES = 60

/** Runs [block], turning failures (but not cancellation) into a [Result]. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.toErrorKind(): ErrorKind = when (this) {
    is ApiException -> if (code == 404) ErrorKind.NOT_FOUND else ErrorKind.SERVER
    is SerializationException, is IllegalArgumentException -> ErrorKind.SERVER
    is IOException -> ErrorKind.NETWORK
    else -> ErrorKind.UNKNOWN
}
