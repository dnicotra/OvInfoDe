package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.data.api.AlertDto
import io.github.dnicotra.ovinfode.data.api.ItineraryDto
import io.github.dnicotra.ovinfode.data.api.MatchDto
import io.github.dnicotra.ovinfode.data.api.PlaceDto
import io.github.dnicotra.ovinfode.data.api.StopTimeDto
import io.github.dnicotra.ovinfode.data.api.StopTimesResponse
import io.github.dnicotra.ovinfode.data.model.Alert
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.StopDepartures
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.data.model.TripStop
import java.time.Instant
import java.time.OffsetDateTime

/* Pure functions that turn API DTOs into the app's models. */

fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull()
}

/** Parses "0066ad" / "#0066AD" into an opaque ARGB int. */
fun parseColor(hex: String?): Int? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return clean.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

private val trackInDescription = Regex("""\b(?:Gleis|Bahnsteig|Steig|Platform)\s+([0-9]+[a-zA-Z]?(?:\s?[A-H](?:-[A-H])?)?)\b""")

/** Platform from real-time/schedule data, or parsed from the stop description ("S-Bahnsteig Gleis 4"). */
internal fun PlaceDto.platformOrNull(): String? =
    track?.takeIf { it.isNotBlank() }
        ?: scheduledTrack?.takeIf { it.isNotBlank() }
        ?: description?.let { trackInDescription.find(it)?.groupValues?.get(1) }

private fun List<AlertDto>?.toAlerts(): List<Alert> =
    this.orEmpty()
        .filter { it.headerText.isNotBlank() || it.descriptionText.isNotBlank() }
        .map { Alert(header = it.headerText.trim(), description = it.descriptionText.trim(), url = it.url?.takeIf(String::isNotBlank)) }

fun MatchDto.toStop(): Stop = Stop(
    id = id,
    name = name,
    lat = lat,
    lon = lon,
    area = areas.firstOrNull { it.default == true }?.name,
    modes = modes.orEmpty().map(TransportMode::fromApi).toSortedSet(),
)

fun PlaceDto.toStop(): Stop = Stop(
    id = stopId ?: "$lat,$lon",
    name = name,
    lat = lat,
    lon = lon,
    modes = modes.orEmpty().map(TransportMode::fromApi).toSortedSet(),
)

private fun lineOf(
    mode: String,
    displayName: String?,
    routeShortName: String?,
    tripShortName: String?,
    routeColor: String?,
    routeTextColor: String?,
): Line {
    val name = displayName?.takeIf { it.isNotBlank() }
        ?: routeShortName?.takeIf { it.isNotBlank() }
        ?: tripShortName?.takeIf { it.isNotBlank() }
        ?: "?"
    val color = parseColor(routeColor)
    return Line(
        name = name.trim(),
        mode = TransportMode.fromApi(mode),
        color = color,
        // A text colour without a background colour is meaningless.
        textColor = if (color != null) parseColor(routeTextColor) else null,
    )
}

fun StopTimeDto.toDepartureOrNull(): Departure? {
    val scheduled = parseInstant(place.scheduledDeparture)
        ?: parseInstant(place.scheduledArrival)
        ?: return null
    val estimated = if (realTime) parseInstant(place.departure) ?: parseInstant(place.arrival) else null
    return Departure(
        tripId = tripId,
        line = lineOf(mode, displayName, routeShortName, tripShortName, routeColor, routeTextColor),
        headsign = headsign?.takeIf { it.isNotBlank() } ?: tripTo?.name ?: "",
        scheduled = scheduled,
        estimated = estimated,
        platform = place.platformOrNull(),
        scheduledPlatform = place.scheduledTrack?.takeIf { it.isNotBlank() },
        cancelled = cancelled || tripCancelled || place.cancelled == true,
        operator = agencyName?.takeIf { it.isNotBlank() },
        stopId = place.stopId,
        stopName = place.name,
        tripNumber = tripShortName?.takeIf { it.isNotBlank() },
        destination = tripTo?.let { NamedPlace(it.stopId, it.name, it.lat, it.lon) },
    )
}

fun StopTimesResponse.toStopDepartures(): StopDepartures {
    val departures = stopTimes
        .mapNotNull { it.toDepartureOrNull() }
        // Overlapping feeds can deliver the same departure twice.
        .distinctBy { Triple(it.line.name, it.headsign, it.scheduled) }
    val alerts = (place?.alerts.toAlerts() + stopTimes.flatMap { it.place.alerts.toAlerts() })
        .distinctBy { it.header to it.description }
    return StopDepartures(
        stop = place?.takeIf { it.stopId != null }?.toStop(),
        departures = departures,
        alerts = alerts,
        nextPageCursor = nextPageCursor?.takeIf { it.isNotBlank() },
    )
}

private fun PlaceDto.toTripStop(realtime: Boolean): TripStop = TripStop(
    id = stopId,
    name = name,
    lat = lat,
    lon = lon,
    scheduledArrival = parseInstant(scheduledArrival),
    arrival = if (realtime) parseInstant(arrival) else null,
    scheduledDeparture = parseInstant(scheduledDeparture),
    departure = if (realtime) parseInstant(departure) else null,
    platform = platformOrNull(),
    scheduledPlatform = scheduledTrack?.takeIf { it.isNotBlank() },
    cancelled = cancelled == true,
)

fun ItineraryDto.toTrip(tripId: String): Trip {
    val transitLegs = legs.filter { it.tripId != null }.ifEmpty { legs }
    require(transitLegs.isNotEmpty()) { "Trip without legs" }
    val first = transitLegs.first()
    val stops = buildList {
        transitLegs.forEachIndexed { index, leg ->
            val legStops = listOf(leg.from) + leg.intermediateStops.orEmpty() + listOf(leg.to)
            // Interlined legs share the transfer stop; keep it once.
            val toAdd = if (index > 0) legStops.drop(1) else legStops
            toAdd.forEach { add(it.toTripStop(leg.realTime)) }
        }
    }
    return Trip(
        id = tripId,
        line = lineOf(first.mode, first.displayName, first.routeShortName, first.tripShortName, first.routeColor, first.routeTextColor),
        headsign = first.headsign?.takeIf { it.isNotBlank() } ?: transitLegs.last().to.name,
        operator = first.agencyName?.takeIf { it.isNotBlank() },
        tripNumber = first.tripShortName?.takeIf { it.isNotBlank() },
        isRealtime = transitLegs.any { it.realTime },
        cancelled = transitLegs.all { it.cancelled == true },
        stops = stops,
        alerts = transitLegs.flatMap { it.alerts.toAlerts() + it.from.alerts.toAlerts() }.distinctBy { it.header to it.description },
    )
}
