package io.github.dnicotra.ovinfode.data.model

import java.time.Duration
import java.time.Instant

data class LatLon(val lat: Double, val lon: Double)

/** A stop as it appears in a trip or as a destination. */
data class NamedPlace(val id: String?, val name: String, val lat: Double, val lon: Double)

/** Transport modes as shown to the user (MOTIS modes folded into familiar German categories). */
enum class TransportMode {
    LONG_DISTANCE, REGIONAL, SUBURBAN, SUBWAY, TRAM, BUS, COACH, FERRY, CABLE, ON_DEMAND, OTHER;

    val isRail: Boolean get() = this == LONG_DISTANCE || this == REGIONAL || this == SUBURBAN || this == SUBWAY

    companion object {
        fun fromApi(mode: String?): TransportMode = when (mode) {
            "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> LONG_DISTANCE
            "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL" -> REGIONAL
            "SUBURBAN" -> SUBURBAN
            "SUBWAY", "METRO" -> SUBWAY
            "TRAM" -> TRAM
            "BUS" -> BUS
            "COACH" -> COACH
            "FERRY" -> FERRY
            "AERIAL_LIFT", "AREAL_LIFT", "FUNICULAR", "CABLE_CAR" -> CABLE
            "ODM", "FLEX", "RIDE_SHARING" -> ON_DEMAND
            else -> OTHER
        }
    }
}

data class Stop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Town or city, if known. */
    val area: String? = null,
    val modes: Set<TransportMode> = emptySet(),
) {
    val location: LatLon get() = LatLon(lat, lon)
}

data class NearbyStop(val stop: Stop, val distanceMeters: Int)

/** Colours are ARGB ints; null means "use the default colour for the mode". */
data class Line(
    val name: String,
    val mode: TransportMode,
    val color: Int? = null,
    val textColor: Int? = null,
)

data class Alert(
    val header: String,
    val description: String,
    val url: String? = null,
)

data class Departure(
    val tripId: String,
    val line: Line,
    val headsign: String,
    val scheduled: Instant,
    /** Real-time estimate; null when the operator sends no real-time data for this trip. */
    val estimated: Instant?,
    val platform: String?,
    val scheduledPlatform: String?,
    val cancelled: Boolean,
    val operator: String?,
    val stopId: String?,
    val stopName: String,
    val tripNumber: String?,
    /** Final stop of the trip; [headsign] is usually its name. */
    val destination: NamedPlace? = null,
) {
    val time: Instant get() = estimated ?: scheduled
    val isRealtime: Boolean get() = estimated != null

    /** Delay in whole minutes, null without real-time data. Negative when early. */
    val delayMinutes: Long? get() = estimated?.let { Duration.between(scheduled, it).toMinutes() }

    val platformChanged: Boolean
        get() = platform != null && scheduledPlatform != null && platform != scheduledPlatform
}

data class StopDepartures(
    val stop: Stop?,
    val departures: List<Departure>,
    val alerts: List<Alert>,
    val nextPageCursor: String?,
)

data class TripStop(
    val id: String?,
    val name: String,
    val lat: Double,
    val lon: Double,
    val scheduledArrival: Instant?,
    val arrival: Instant?,
    val scheduledDeparture: Instant?,
    val departure: Instant?,
    val platform: String?,
    val scheduledPlatform: String?,
    val cancelled: Boolean,
) {
    val place: NamedPlace get() = NamedPlace(id, name, lat, lon)

    /** Best known arrival time (real-time if available). */
    val effectiveArrival: Instant? get() = arrival ?: scheduledArrival ?: effectiveDepartureOrNull()
    /** Best known departure time (real-time if available). */
    val effectiveDeparture: Instant? get() = effectiveDepartureOrNull() ?: arrival ?: scheduledArrival

    private fun effectiveDepartureOrNull() = departure ?: scheduledDeparture

    val platformChanged: Boolean
        get() = platform != null && scheduledPlatform != null && platform != scheduledPlatform
}

data class Trip(
    val id: String,
    val line: Line,
    val headsign: String,
    val operator: String?,
    val tripNumber: String?,
    val isRealtime: Boolean,
    val cancelled: Boolean,
    val stops: List<TripStop>,
    val alerts: List<Alert>,
)

enum class ErrorKind { NETWORK, SERVER, NOT_FOUND, UNKNOWN }
