package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.districtOf

/**
 * Germany: the nationwide DELFI timetable. German stop ids (DHID) look like
 * "de:08111:6115:2:3" (country, district, station, platform). Stop names are shortened the
 * standard German way by [io.github.dnicotra.ovinfode.util.shortStopName], for every country.
 */
object Germany : Country("de") {
    override val outline = Outline(CountryOutlines.DE)

    /**
     * Some local networks name their stops without the town: in the DELFI data, Stuttgart Hbf
     * is just "Hauptbahnhof". That is fine on a Stuttgart departure board, but an ICE from
     * Heidelberg "to Hauptbahnhof" is useless. Seen from the same district, the name is clear.
     */
    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean {
        if (!super.needsTown(place, contextStopId)) return false
        val district = districtOf(place.id)
        val context = districtOf(contextStopId)
        return district == null || context == null || district != context
    }

    override fun modeLabel(mode: TransportMode): Int? = when (mode) {
        TransportMode.SUBURBAN -> R.string.mode_suburban_de
        TransportMode.SUBWAY -> R.string.mode_subway_de
        else -> null
    }
}
