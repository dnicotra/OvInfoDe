package io.github.dnicotra.ovinfode.util

import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.TripStop
import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Great-circle distance in metres. */
fun distanceMeters(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** (south-west, north-east) corners of a square around [center]. */
fun boundingBox(center: LatLon, radiusMeters: Int): Pair<LatLon, LatLon> {
    val dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_M)
    val dLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_M * cos(Math.toRadians(center.lat))))
    return LatLon(center.lat - dLat, center.lon - dLon) to LatLon(center.lat + dLat, center.lon + dLon)
}

/** What the countdown column shows. */
sealed interface Countdown {
    data object Now : Countdown
    data class Minutes(val minutes: Long) : Countdown
    /** More than an hour away: show the clock time instead. */
    data class Clock(val time: Instant) : Countdown
}

fun countdown(departure: Instant, now: Instant): Countdown {
    val seconds = Duration.between(now, departure).seconds
    if (seconds < 45) return Countdown.Now
    val minutes = (seconds + 15) / 60 // 1:45 → "2 min"
    return if (minutes >= 60) Countdown.Clock(departure) else Countdown.Minutes(minutes)
}

/** Departures that have not left yet (with a little grace so "now" stays visible). */
fun List<Departure>.upcoming(now: Instant): List<Departure> =
    filter { !it.time.isBefore(now.minusSeconds(30)) }

/** Departures grouped by line and direction, in order of their next departure. */
data class LineGroup(val key: String, val departures: List<Departure>) {
    val first: Departure get() = departures.first()
}

fun List<Departure>.groupByLine(): List<LineGroup> =
    groupBy { "${it.line.mode}|${it.line.name}|${it.headsign}" }
        .map { (key, list) -> LineGroup(key, list.sortedBy { it.time }) }
        .sortedBy { it.first.time }

/** Where the vehicle of a trip is right now, derived from the (real-time) stop times. */
sealed interface VehiclePosition {
    data object NotStarted : VehiclePosition
    data class AtStop(val index: Int) : VehiclePosition
    /** Between stop [fromIndex] and the next stop. */
    data class Between(val fromIndex: Int) : VehiclePosition
    data object Finished : VehiclePosition
}

fun vehiclePosition(stops: List<TripStop>, now: Instant): VehiclePosition {
    if (stops.isEmpty()) return VehiclePosition.NotStarted
    val firstDeparture = stops.first().effectiveDeparture ?: return VehiclePosition.NotStarted
    if (now.isBefore(firstDeparture)) return VehiclePosition.NotStarted
    for (i in stops.indices) {
        val arrival = stops[i].effectiveArrival ?: continue
        val departure = stops[i].effectiveDeparture ?: arrival
        if (i > 0 && now.isBefore(arrival)) return VehiclePosition.Between(previousWithTime(stops, i))
        if (i < stops.lastIndex && now.isBefore(departure)) return VehiclePosition.AtStop(i)
    }
    return VehiclePosition.Finished
}

private fun previousWithTime(stops: List<TripStop>, index: Int): Int {
    var i = index - 1
    while (i > 0 && stops[i].effectiveDeparture == null) i--
    return i
}

/** Index of the stop the user came from, so the trip view can highlight it. */
fun indexOfStop(stops: List<TripStop>, stopId: String?, stopName: String?): Int {
    if (stopId != null) {
        val byId = stops.indexOfFirst { it.id == stopId }
        if (byId >= 0) return byId
    }
    if (stopName != null) return stops.indexOfFirst { it.name == stopName }
    return -1
}
