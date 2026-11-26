package io.github.dnicotra.ovinfode.data

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
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class TransitRepository(
    private val api: TransitousApi,
    private val towns: TownResolver = TownResolver(api),
) {

    suspend fun searchStops(query: String, near: LatLon?): List<Stop> =
        api.geocode(query, near)
            .filter { it.type == "STOP" }
            .map { it.toStop() }
            .distinctBy { it.id }

    /**
     * Stops around [center], closest first. Starts with a small radius and widens it in
     * rural areas until a few stops are found.
     */
    suspend fun nearbyStops(center: LatLon, limit: Int = 15): List<NearbyStop> {
        var radius = 500
        var result: List<NearbyStop> = emptyList()
        while (radius <= 4000) {
            val (min, max) = boundingBox(center, radius)
            result = api.mapStops(min, max)
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
        val result = api.stopTimes(stop.id, stop.location, count, pageCursor).toStopDepartures()
        return result.copy(departures = withTowns(result.departures, contextStopId = stop.id))
    }

    /** A trip with all its stops; [contextStopId] is the stop the user came from. */
    suspend fun trip(tripId: String, contextStopId: String? = null): Trip {
        val trip = api.trip(tripId).toTrip(tripId)
        val context = contextStopId ?: trip.stops.firstOrNull()?.id
        val candidates = trip.stops.map { it.place }.filter { it.country().needsTown(it, context) }
        if (candidates.isEmpty()) return trip
        val known = towns.towns(candidates)
        if (known.isEmpty()) return trip
        val stops = trip.stops.map { stop ->
            known[placeKey(stop.place)]?.let { town -> stop.copy(name = stop.place.country().stopName(qualifyName(stop.name, town))) } ?: stop
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
            val name = destination.country().stopName(qualifyName(departure.headsign, town))
            departure.copy(headsign = name, destination = departure.destination?.copy(name = name))
        }
    }
}

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
