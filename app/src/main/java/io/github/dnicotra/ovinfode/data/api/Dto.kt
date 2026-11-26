package io.github.dnicotra.ovinfode.data.api

import kotlinx.serialization.Serializable

/*
 * Subset of the MOTIS 2 API (https://api.transitous.org/api/) used by the app.
 * Everything that is not needed is ignored when decoding, and every field that the
 * spec does not mark as required is nullable, so new server versions do not break us.
 */

@Serializable
data class AreaDto(
    val name: String,
    val adminLevel: Double? = null,
    val default: Boolean? = null,
)

/** Result of `/api/v1/geocode` and `/api/v1/reverse-geocode`. */
@Serializable
data class MatchDto(
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val areas: List<AreaDto> = emptyList(),
    val modes: List<String>? = null,
)

@Serializable
data class AlertDto(
    val headerText: String = "",
    val descriptionText: String = "",
    val url: String? = null,
    val severityLevel: String? = null,
)

@Serializable
data class PlaceDto(
    val name: String,
    val stopId: String? = null,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val description: String? = null,
    val cancelled: Boolean? = null,
    val alerts: List<AlertDto>? = null,
    val modes: List<String>? = null,
    val importance: Double? = null,
)

/** One departure in `/api/v6/stoptimes`. */
@Serializable
data class StopTimeDto(
    val place: PlaceDto,
    val mode: String,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val tripTo: PlaceDto? = null,
    val agencyName: String? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val tripId: String,
    val routeShortName: String? = null,
    val tripShortName: String? = null,
    val displayName: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)

@Serializable
data class StopTimesResponse(
    val stopTimes: List<StopTimeDto>,
    val place: PlaceDto? = null,
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Serializable
data class LegDto(
    val mode: String,
    val from: PlaceDto,
    val to: PlaceDto,
    val intermediateStops: List<PlaceDto>? = null,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val tripTo: PlaceDto? = null,
    val routeColor: String? = null,
    val routeTextColor: String? = null,
    val agencyName: String? = null,
    val routeShortName: String? = null,
    val tripShortName: String? = null,
    val displayName: String? = null,
    val tripId: String? = null,
    val cancelled: Boolean? = null,
    val alerts: List<AlertDto>? = null,
)

/** Result of `/api/v6/trip`. */
@Serializable
data class ItineraryDto(
    val legs: List<LegDto>,
)

@Serializable
data class ErrorDto(
    val error: String? = null,
)
