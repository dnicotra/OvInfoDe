package io.github.dnicotra.ovinfode

import io.github.dnicotra.ovinfode.data.api.ItineraryDto
import io.github.dnicotra.ovinfode.data.api.StopTimesResponse
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.StopDepartures
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.data.toStopDepartures
import io.github.dnicotra.ovinfode.data.toTrip

/**
 * Real Transitous responses recorded on 2026-11-26: the German ones around 07:05 local time
 * (06:05 UTC), timetable data © DELFI e.V. and the respective operators; the Dutch ones
 * (amsterdam, utrecht, ic2735, iff) around 10:10 local time (09:10 UTC), data from OpenOV (CC0).
 * The Luxembourg ones (luxembourg, lu_re5113) were recorded at 00:50 local time for the next
 * morning from 08:30 (07:30 UTC), so without real-time data: timetables of the Administration
 * des transports publics, DELFI e.V. and SNCF.
 */
object Fixtures {
    fun text(name: String): String = requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    /** Every recorded response except the reverse geocoding answers. */
    val all = listOf(
        "stoptimes_berlin_alexanderplatz.json", "stoptimes_koeln_hbf.json", "stoptimes_heidelberg_hbf.json",
        "trip_s3.json", "trip_ice1697.json", "geocode_alexanderplatz.json", "mapstops_koeln.json",
        "stoptimes_amsterdam_centraal.json", "stoptimes_utrecht_centraal.json", "stoptimes_amsterdam_leidseplein.json",
        "trip_ic2735.json", "trip_iff_ic3531.json", "mapstops_amsterdam_dam.json",
        "stoptimes_luxembourg_gare.json", "stoptimes_luxembourg_gare_tram.json", "trip_lu_re5113.json", "mapstops_luxembourg_gare.json",
    )

    fun stopTimes(name: String): StopDepartures =
        TransitousApi.json.decodeFromString<StopTimesResponse>(text(name)).toStopDepartures()

    val berlin: StopDepartures get() = stopTimes("stoptimes_berlin_alexanderplatz.json")
    val koeln: StopDepartures get() = stopTimes("stoptimes_koeln_hbf.json")
    val tripS3: Trip get() = TransitousApi.json.decodeFromString<ItineraryDto>(text("trip_s3.json")).toTrip("20261126_06:24_de-VBB_308692668")

    val amsterdam: StopDepartures get() = stopTimes("stoptimes_amsterdam_centraal.json")
    val utrecht: StopDepartures get() = stopTimes("stoptimes_utrecht_centraal.json")

    /** Luxembourg station (the stop of the French feed, which groups the platforms of every feed). */
    val luxembourg: StopDepartures get() = stopTimes("stoptimes_luxembourg_gare.json")

    fun trip(name: String, tripId: String, language: String = "en"): Trip =
        TransitousApi.json.decodeFromString<ItineraryDto>(text(name)).toTrip(tripId, language)
}
