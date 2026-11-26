package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.data.api.ApiException
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.StopDepartures
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.util.boundingBox
import io.github.dnicotra.ovinfode.util.distanceMeters
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class TransitRepository(private val api: TransitousApi) {

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

    suspend fun departures(stop: Stop, count: Int, pageCursor: String? = null): StopDepartures =
        api.stopTimes(stop.id, stop.location, count, pageCursor).toStopDepartures()

    suspend fun trip(tripId: String): Trip = api.trip(tripId).toTrip(tripId)
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
