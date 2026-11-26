package io.github.dnicotra.ovinfode

import io.github.dnicotra.ovinfode.data.api.ItineraryDto
import io.github.dnicotra.ovinfode.data.api.StopTimesResponse
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.StopDepartures
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.data.toStopDepartures
import io.github.dnicotra.ovinfode.data.toTrip

/**
 * Real Transitous responses recorded on 2026-11-26 around 07:05 local time (06:05 UTC).
 * Timetable data © DELFI e.V. and the respective operators, via Transitous.
 */
object Fixtures {
    fun text(name: String): String = requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

    fun stopTimes(name: String): StopDepartures =
        TransitousApi.json.decodeFromString<StopTimesResponse>(text(name)).toStopDepartures()

    val berlin: StopDepartures get() = stopTimes("stoptimes_berlin_alexanderplatz.json")
    val koeln: StopDepartures get() = stopTimes("stoptimes_koeln_hbf.json")
    val tripS3: Trip get() = TransitousApi.json.decodeFromString<ItineraryDto>(text("trip_s3.json")).toTrip("20261126_06:24_de-VBB_308692668")
}
