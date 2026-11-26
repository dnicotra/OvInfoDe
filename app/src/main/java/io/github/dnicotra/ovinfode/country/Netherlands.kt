package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.data.model.TransportMode

/**
 * The Netherlands: the national OpenOV timetable and real-time feeds (NS trains, all buses,
 * trams and metros). Bus, tram and metro stops are named "Town, Stop", train stations after
 * their town ("Utrecht Centraal"), so destinations never need a town lookup.
 */
object Netherlands : Country("nl") {
    override val outline = Outline(CountryOutlines.NL)

    override fun needsTown(place: NamedPlace, contextStopId: String?): Boolean = false

    /** Trains with Dutch signage colours: NS yellow with NS blue text. */
    val TRAIN_COLOR = 0xFFFFC917.toInt()
    val TRAIN_TEXT_COLOR = 0xFF003082.toInt()

    // NS names its products in full; on platforms and in apps they are "IC" and "SPR".
    private val trainTypes = mapOf("Intercity" to "IC", "Sprinter" to "SPR", "Intercity direct" to "ICD")

    // Rail replacement buses: "Snelbus ipv trein" ("express bus instead of train") → "Snelbus".
    private val replacementBus = Regex("""\s+i\.?p\.?v\.?\s+trein$""", RegexOption.IGNORE_CASE)

    // Trains added in real time come without a route; their id still says the train type:
    // "20261126_09:12_nl-OpenOV_2026-11-26:IFF:IC:303531".
    private val iffTrainType = Regex(""":IFF:([A-Z]{1,4}):""")
    private val longDistanceTypes = setOf("ICD", "ICE", "EST", "ECD", "EC", "NJ", "INT", "THA")

    override fun line(line: Line, tripId: String): Line {
        var name = trainTypes[line.name] ?: line.name.replace(replacementBus, "")
        var mode = line.mode
        if (name == "?") {
            iffTrainType.find(tripId)?.groupValues?.get(1)?.let { type ->
                name = type
                if (mode == TransportMode.OTHER) mode = if (type in longDistanceTypes) TransportMode.LONG_DISTANCE else TransportMode.REGIONAL
            }
        }
        val train = mode == TransportMode.LONG_DISTANCE || mode == TransportMode.REGIONAL || mode == TransportMode.SUBURBAN
        return if (train && line.color == null) {
            line.copy(name = name, mode = mode, color = TRAIN_COLOR, textColor = TRAIN_TEXT_COLOR)
        } else {
            line.copy(name = name, mode = mode)
        }
    }

    // The feed uses the official name of The Hague; everyone, NS included, says "Den Haag".
    private val hague = Regex("""^'s-Gravenhage(?=$|[ ,])""")

    override fun localStopName(name: String): String = hague.replace(name, "Den Haag")

    /** GVB sends alerts as "Dutch text -- English text": show the Dutch part to Dutch readers, the English one to everyone else. */
    override fun alertText(text: String, language: String): String {
        val parts = text.split(" -- ")
        if (parts.size != 2 || parts.any { it.isBlank() }) return text
        return (if (language == code) parts[0] else parts[1]).trim()
    }

    /** All domestic trains (Intercity and Sprinter alike) are "regional" in the data; to the Dutch they are just trains. */
    override fun modeLabel(mode: TransportMode): Int? = if (mode == TransportMode.REGIONAL) R.string.mode_train else null
}
