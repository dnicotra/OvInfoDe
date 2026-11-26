package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.country.Countries
import io.github.dnicotra.ovinfode.country.Country
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
import io.github.dnicotra.ovinfode.util.cleanAlertText
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/*
 * Pure functions that turn API DTOs into the app's models. Names and lines follow the
 * conventions of the country they belong to (see [Country]).
 */

private fun defaultLanguage(): String = Locale.getDefault().language

private fun PlaceDto.country(): Country = Countries.of(stopId, lat, lon)

/** The place's name, by the rules of the country it lies in and of the feed it comes from. */
private fun PlaceDto.displayName(): String = country().stopName(name, Countries.ofFeed(stopId))

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

private val trackInDescription = Regex("""\b(?:Gleis|Bahnsteig|Steig|Platform|Spoor|Perron|Binario)\s+([0-9]+[a-zA-Z]?(?:\s?[A-H](?:-[A-H])?)?)\b""")

/** Platform from real-time/schedule data, or parsed from the stop description ("S-Bahnsteig Gleis 4"). */
internal fun PlaceDto.platformOrNull(): String? =
    track?.takeIf { it.isNotBlank() }
        ?: scheduledTrack?.takeIf { it.isNotBlank() }
        ?: description?.let { trackInDescription.find(it)?.groupValues?.get(1) }

private fun List<AlertDto>?.toAlerts(country: Country, language: String): List<Alert> =
    this.orEmpty()
        .filter { it.headerText.isNotBlank() || it.descriptionText.isNotBlank() }
        .map {
            val description = cleanAlertText(it.descriptionText).trim()
            // Some feeds (GVB in Amsterdam) cut long headers off mid-word; the description has all of it.
            val header = cleanAlertText(it.headerText).trim().let { h -> if (h.length >= 100 && description.length > h.length && description.startsWith(h)) description else h }
            Alert(
                header = country.alertText(header, language).trim(),
                description = country.alertText(description, language).trim(),
                url = it.url?.takeIf(String::isNotBlank),
            )
        }

/** MOTIS fills missing names with "?". */
private fun String?.realNameOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "?" }

fun MatchDto.toStop(): Stop = Stop(
    id = id,
    name = Countries.of(id, lat, lon).stopName(name, Countries.ofFeed(id)),
    lat = lat,
    lon = lon,
    area = areas.firstOrNull { it.default == true }?.name,
    modes = modes.orEmpty().map(TransportMode::fromApi).toSortedSet(),
)

fun PlaceDto.toStop(): Stop = Stop(
    id = stopId ?: "$lat,$lon",
    name = displayName(),
    lat = lat,
    lon = lon,
    modes = modes.orEmpty().map(TransportMode::fromApi).toSortedSet(),
)

/** The line of trip [tripId], named the way the country of its timetable feed does (or else [here]'s). */
private fun lineOf(
    tripId: String,
    here: Country,
    mode: String,
    displayName: String?,
    routeShortName: String?,
    tripShortName: String?,
    routeColor: String?,
    routeTextColor: String?,
): Line {
    val name = displayName.realNameOrNull()
        ?: routeShortName.realNameOrNull()
        ?: tripShortName.realNameOrNull()
        ?: "?"
    val color = parseColor(routeColor)
    val line = Line(
        name = name,
        mode = TransportMode.fromApi(mode),
        color = color,
        // A text colour without a background colour is meaningless.
        textColor = if (color != null) parseColor(routeTextColor) else null,
    )
    return (Countries.ofTrip(tripId) ?: here).line(line, tripId)
}

/** The operator to show, by the rules of the country of the trip's feed (or else [here]'s). */
private fun operatorOf(tripId: String, here: Country, agencyName: String?): String? =
    agencyName?.trim()?.takeIf { it.isNotEmpty() }?.let { (Countries.ofTrip(tripId) ?: here).operatorName(it) }

/** The trip number to show, by the rules of the country of the trip's feed (or else [here]'s). */
private fun tripNumberOf(tripId: String, here: Country, tripShortName: String?, line: Line): String? =
    tripShortName.realNameOrNull()?.let { (Countries.ofTrip(tripId) ?: here).tripNumber(it, line) }

fun StopTimeDto.toDepartureOrNull(): Departure? {
    val scheduled = parseInstant(place.scheduledDeparture)
        ?: parseInstant(place.scheduledArrival)
        ?: return null
    val estimated = if (realTime) parseInstant(place.departure) ?: parseInstant(place.arrival) else null
    val here = place.country()
    val destinationCountry = tripTo?.country() ?: here
    val line = lineOf(tripId, here, mode, displayName, routeShortName, tripShortName, routeColor, routeTextColor)
    return Departure(
        tripId = tripId,
        line = line,
        headsign = destinationCountry.stopName(headsign?.takeIf { it.isNotBlank() } ?: tripTo?.name ?: "", Countries.ofTrip(tripId)),
        scheduled = scheduled,
        estimated = estimated,
        platform = place.platformOrNull(),
        scheduledPlatform = place.scheduledTrack?.takeIf { it.isNotBlank() },
        cancelled = cancelled || tripCancelled || place.cancelled == true,
        operator = operatorOf(tripId, here, agencyName),
        stopId = place.stopId,
        stopName = place.displayName(),
        tripNumber = tripNumberOf(tripId, here, tripShortName, line),
        destination = tripTo?.let { NamedPlace(it.stopId, it.displayName(), it.lat, it.lon) },
    )
}

/** [language] picks the part of multilingual alerts to show. */
fun StopTimesResponse.toStopDepartures(language: String = defaultLanguage()): StopDepartures {
    val departures = stopTimes
        .mapNotNull { it.toDepartureOrNull() }
        // Copies that only a country's rules recognise (Trenitalia's outdated versions of a train).
        .let { all -> Countries.all.fold(all) { list, country -> country.withoutDuplicates(list) } }
        // Overlapping feeds can deliver the same departure twice, and NS replaces a train by
        // cancelling it and adding a new one: keep the one with real-time data that runs.
        .groupBy { Triple(it.line.name, it.headsign, it.scheduled) }
        .map { (_, same) -> same.minWith(compareBy({ !it.isRealtime }, { it.cancelled })) }
    val alerts = (
        place?.let { it.alerts.toAlerts(it.country(), language) }.orEmpty() +
            stopTimes.flatMap { it.place.alerts.toAlerts(it.place.country(), language) }
        )
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
    name = displayName(),
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

fun ItineraryDto.toTrip(tripId: String, language: String = defaultLanguage()): Trip {
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
    val origin = first.from.country()
    val firstTripId = first.tripId ?: tripId
    val line = lineOf(firstTripId, origin, first.mode, first.displayName, first.routeShortName, first.tripShortName, first.routeColor, first.routeTextColor)
    return Trip(
        id = tripId,
        line = line,
        headsign = transitLegs.last().to.country().stopName(first.headsign?.takeIf { it.isNotBlank() } ?: transitLegs.last().to.name, Countries.ofTrip(firstTripId)),
        operator = operatorOf(firstTripId, origin, first.agencyName),
        tripNumber = tripNumberOf(firstTripId, origin, first.tripShortName, line),
        isRealtime = transitLegs.any { it.realTime },
        cancelled = transitLegs.all { it.cancelled == true },
        stops = stops,
        alerts = transitLegs
            .flatMap { it.alerts.toAlerts(it.from.country(), language) + it.from.alerts.toAlerts(it.from.country(), language) }
            .distinctBy { it.header to it.description },
    )
}
