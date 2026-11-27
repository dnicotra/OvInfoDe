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
 * The Italian ones were recorded on 2026-11-27 at 00:30 local time (23:30 UTC on the 26th): the
 * timetables for the morning (Roma Termini from 08:00, the others from 08:30; no real-time data at
 * that hour) and the night buses at Torino Porta Nuova as they ran, from Trenitalia, ATAC/Roma
 * Mobilità, ATM, Trenord and GTT via Transitous (and BusOne).
 * The Luxembourg ones (luxembourg, lu_re5113) were recorded at 00:50 local time for the next
 * morning from 08:30 (07:30 UTC), so without real-time data: timetables of the Administration
 * des transports publics, DELFI e.V. and SNCF.
 * The Belgian ones (bruxelles, antwerpen, liege, be_ic, tec_b2) were recorded on 2026-11-27
 * for 08:30 local time (07:30 UTC), in the language the app asks for there; data from SNCB,
 * STIB, De Lijn and TEC (CC BY 4.0).
 */
object Fixtures {
    fun text(name: String): String = requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    /** The Italian recordings. */
    val italian = listOf(
        "stoptimes_roma_termini.json", "stoptimes_roma_termini_bus.json", "stoptimes_milano_duomo.json",
        "stoptimes_milano_cadorna.json", "stoptimes_torino_porta_nuova.json", "trip_fr9490.json", "mapstops_milano_duomo.json",
    )

    /** The Luxembourg recordings. */
    val luxembourgish = listOf(
        "stoptimes_luxembourg_gare.json", "stoptimes_luxembourg_gare_tram.json", "trip_lu_re5113.json", "mapstops_luxembourg_gare.json",
    )

    /** The Belgian recordings. */
    val belgian = listOf(
        "stoptimes_bruxelles_central.json", "stoptimes_bruxelles_de_brouckere.json", "stoptimes_antwerpen_centraal.json",
        "stoptimes_antwerpen_centraal_station_delijn.json", "stoptimes_liege_guillemins.json",
        "trip_be_ic.json", "trip_tec_b2.json", "mapstops_bruxelles_centre.json",
    )

    /** Every recorded response except the reverse geocoding answers. */
    val all = listOf(
        "stoptimes_berlin_alexanderplatz.json", "stoptimes_koeln_hbf.json", "stoptimes_heidelberg_hbf.json",
        "trip_s3.json", "trip_ice1697.json", "geocode_alexanderplatz.json", "mapstops_koeln.json",
        "stoptimes_amsterdam_centraal.json", "stoptimes_utrecht_centraal.json", "stoptimes_amsterdam_leidseplein.json",
        "trip_ic2735.json", "trip_iff_ic3531.json", "mapstops_amsterdam_dam.json",
    ) + italian + luxembourgish + belgian

    fun stopTimes(name: String): StopDepartures =
        TransitousApi.json.decodeFromString<StopTimesResponse>(text(name)).toStopDepartures()

    val berlin: StopDepartures get() = stopTimes("stoptimes_berlin_alexanderplatz.json")
    val koeln: StopDepartures get() = stopTimes("stoptimes_koeln_hbf.json")
    val tripS3: Trip get() = TransitousApi.json.decodeFromString<ItineraryDto>(text("trip_s3.json")).toTrip("20261126_06:24_de-VBB_308692668")

    val amsterdam: StopDepartures get() = stopTimes("stoptimes_amsterdam_centraal.json")
    val utrecht: StopDepartures get() = stopTimes("stoptimes_utrecht_centraal.json")

    val romaTermini: StopDepartures get() = stopTimes("stoptimes_roma_termini.json")
    val milanoDuomo: StopDepartures get() = stopTimes("stoptimes_milano_duomo.json")

    /** Luxembourg station (the stop of the French feed, which groups the platforms of every feed). */
    val luxembourg: StopDepartures get() = stopTimes("stoptimes_luxembourg_gare.json")

    fun trip(name: String, tripId: String, language: String = "en"): Trip =
        TransitousApi.json.decodeFromString<ItineraryDto>(text(name)).toTrip(tripId, language)
}
