package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.country.Belgium
import io.github.dnicotra.ovinfode.country.France
import io.github.dnicotra.ovinfode.country.Italy
import io.github.dnicotra.ovinfode.country.Luxembourg
import io.github.dnicotra.ovinfode.country.Netherlands
import io.github.dnicotra.ovinfode.country.country
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.districtOf
import io.github.dnicotra.ovinfode.util.indexOfStop
import io.github.dnicotra.ovinfode.util.vehiclePosition
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks the app's data layer against the real Transitous API (plain JVM, no emulator).
 * Skipped by default; run with
 * `./gradlew testDebugUnitTest -PliveApi --tests '*LiveApiTest'`.
 * Keep it small: Transitous is a volunteer-run service.
 */
class LiveApiTest {
    private val townLookups = AtomicInteger()
    private val repository = TransitRepository(
        TransitousApi(
            userAgent = "Abfahrten-tests/0.1",
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    if (chain.request().url.encodedPath.endsWith("reverse-geocode")) townLookups.incrementAndGet()
                    chain.proceed(chain.request())
                }
                .build(),
        ),
    )

    /** Destination names that say nothing without a town. */
    private val bareNames = setOf("hauptbahnhof", "hbf", "bahnhof", "bf", "zob", "busbahnhof")

    private val stations = listOf(
        "Berlin Hbf", "Hamburg Hbf", "München Hbf", "Köln Hbf",
        "Frankfurt (Main) Hbf", "Stuttgart Hbf", "Heidelberg Hbf", "Eberswalde Hbf",
    )

    @Before
    fun onlyWhenRequested() = assumeTrue("live API tests disabled", System.getProperty("liveApi") == "true")

    @Test
    fun `departure boards across Germany load with readable destinations`() = runBlocking {
        val problems = mutableListOf<String>()
        for (query in stations) {
            val stop = repository.searchStops(query, near = null).firstOrNull()
            if (stop == null) {
                problems += "$query: not found"
                continue
            }
            val departures = repository.departures(stop, count = 30).departures
            val realtime = if (departures.isEmpty()) 0 else departures.count { it.isRealtime } * 100 / departures.size
            println(String.format("%-36s %3d departures  %3d%% real-time  %2d cancelled  modes %s",
                stop.name.take(36), departures.size, realtime, departures.count { it.cancelled },
                departures.map { it.line.mode }.distinct().sorted()))

            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            departures.filter { it.headsign.isBlank() }.forEach { problems += "${stop.name}: ${it.line.name} has no destination" }
            // A bare "Hauptbahnhof" is only acceptable for destinations in the same city.
            departures
                .filter { it.headsign.trim().lowercase() in bareNames && districtOf(it.destination?.id) != districtOf(stop.id) }
                .forEach { problems += "${stop.name}: ${it.line.name} → '${it.headsign}' (${it.destination?.id})" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `long-distance trip has its stops, times and the stop we came from`() = runBlocking {
        val stop = repository.searchStops("Frankfurt (Main) Hbf", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.mode == TransportMode.LONG_DISTANCE && !it.cancelled }
        assumeTrue("no long-distance departure right now", departure != null)
        departure!!

        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        println("${trip.line.name} → ${trip.headsign}: " + trip.stops.joinToString(" – ") { it.name })

        assertTrue("too few stops", trip.stops.size >= 2)
        assertTrue("origin stop not found", indexOfStop(trip.stops, departure.stopId, departure.stopName) >= 0)
        assertTrue("bare final stop '${trip.stops.last().name}'", trip.stops.last().name.lowercase() !in bareNames)
        val times = trip.stops.mapNotNull { it.scheduledDeparture ?: it.scheduledArrival }
        assertTrue("scheduled times go backwards", times.zipWithNext().all { (a, b) -> !b.isBefore(a) })
        println("vehicle: ${vehiclePosition(trip.stops, Instant.now())}")
    }

    @Test
    fun `stops along a trip open as full departure boards`() = runBlocking {
        val stop = repository.searchStops("Frankfurt (Main) Hbf", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.mode == TransportMode.REGIONAL && !it.cancelled }
        assumeTrue("no regional departure right now", departure != null)
        departure!!

        // Trip stops carry platform-level ids; the board must still cover the whole station.
        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        val next = trip.stops.drop(1).firstNotNullOfOrNull { it.place.toStopOrNull() }
        assumeTrue("trip has no further stop with an id", next != null)
        val board = repository.departures(next!!, count = 30).departures
        println("${next.name} (${next.id}): ${board.size} departures, modes ${board.map { it.line.mode }.distinct().sorted()}")
        assertTrue("no departures at ${next.name}", board.isNotEmpty())

        val destination = departure.destination?.toStopOrNull()
        assumeTrue("departure has no destination stop", destination != null)
        println("destination ${destination!!.name} (${destination.id})")
        // The final stop may have no departures left today, but the request has to work.
        repository.departures(destination, count = 5)
        Unit
    }

    @Test
    fun `stop search finds stations by name`() = runBlocking {
        for ((query, expected) in listOf("Alexanderplatz" to "Alexanderplatz", "Marienplatz München" to "Marienplatz", "Köln Dom" to "Dom")) {
            val first = repository.searchStops(query, near = null).firstOrNull()
            println("'$query' → ${first?.name} (${first?.area})")
            assertTrue("'$query' gave ${first?.name}", first != null && first.name.contains(expected))
        }
    }

    @Test
    fun `dutch departure boards use Dutch line names and need no town lookups`() = runBlocking {
        val problems = mutableListOf<String>()
        val stations = listOf(
            "Amsterdam Centraal", "Utrecht Centraal", "Rotterdam Centraal", "Den Haag Centraal",
            "Groningen", "Maastricht", "Amsterdam, Leidseplein",
        )
        for (query in stations) {
            val stop = repository.searchStops(query, near = null).firstOrNull()
            if (stop == null || stop.country() != Netherlands) {
                problems += "$query: found ${stop?.name} (${stop?.id}, ${stop?.country()})"
                continue
            }
            val before = townLookups.get()
            val departures = repository.departures(stop, count = 30).departures
            val lookups = townLookups.get() - before
            println(String.format("%-36s %3d departures  %2d lookups  lines %s", stop.name.take(36), departures.size, lookups,
                departures.map { it.line.name }.distinct().take(14)))
            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            // Only destinations abroad (Aachen, Liège from Maastricht) may need one.
            val abroad = departures.mapNotNull { it.destination }.filter { it.country() != Netherlands }.distinctBy { it.name }
            if (lookups > abroad.size) problems += "${stop.name}: $lookups town lookups for ${abroad.map { it.name }}"
            departures.filter { it.headsign.isBlank() }.forEach { problems += "${stop.name}: ${it.line.name} has no destination" }
            departures.filter { it.line.name in setOf("Intercity", "Sprinter", "?") }
                .forEach { problems += "${stop.name}: line '${it.line.name}' (${it.tripId})" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `dutch train trip opens with its stops`() = runBlocking {
        val stop = repository.searchStops("Utrecht Centraal", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.name == "IC" && !it.cancelled && !it.tripId.contains(":IFF:") }
        assumeTrue("no Intercity right now", departure != null)
        departure!!
        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        println("${trip.line.name} ${trip.tripNumber} → ${trip.headsign}: " + trip.stops.joinToString(" – ") { "${it.name} (${it.platform})" })
        assertTrue("too few stops", trip.stops.size >= 2)
        assertTrue("origin stop not found", indexOfStop(trip.stops, departure.stopId, departure.stopName) >= 0)
        assertTrue("no platforms", trip.stops.any { it.platform != null })
    }

    /** A German reader, for whom Belgian names must not come out bilingual or abbreviated. */
    private val germanReader = TransitRepository(TransitousApi(userAgent = "Abfahrten-tests/0.1"), userLanguage = { "de" })

    /** What Belgian names must not look like: "Leuven / Louvain", "Brus.-/ Brux-Centr.", "LIEGE Opéra", "… perron 2". */
    private fun belgianNameProblem(name: String): String? = when {
        name.isBlank() -> "empty"
        " / " in name || "/ " in name -> "bilingual"
        Regex("""\b(?:Brus|Brux)\.""").containsMatchIn(name) -> "abbreviated"
        Regex("""^[A-ZÀ-Ý'\-]{3,}(?: [A-ZÀ-Ý'\-]{2,})* [A-ZÀ-Ý]?[a-zà-ÿ]""").containsMatchIn(name) -> "town in capitals"
        Regex("""\bperron \w+$""").containsMatchIn(name) -> "platform in the name"
        else -> null
    }

    @Test
    fun `belgian departure boards read like the signs, for German readers too`() = runBlocking {
        val problems = mutableListOf<String>()
        val stations = listOf(
            "Bruxelles-Central", "Antwerpen-Centraal", "Gent-Sint-Pieters", "Liège-Guillemins", "Charleroi-Central",
            "Namur", "Brugge", "De Brouckère", "Gent Korenmarkt", "Antwerpen Groenplaats", "Charleroi Gare du Sud",
        )
        for (query in stations) {
            val stop = germanReader.searchStops(query, near = null).firstOrNull()
            if (stop == null || stop.country() != Belgium) {
                problems += "$query: found ${stop?.name} (${stop?.id}, ${stop?.country()})"
                continue
            }
            belgianNameProblem(stop.name)?.let { problems += "$query: found '${stop.name}': $it" }
            val departures = germanReader.departures(stop, count = 30).departures
            val realtime = if (departures.isEmpty()) 0 else departures.count { it.isRealtime } * 100 / departures.size
            println(String.format("%-30s %3d departures  %3d%% real-time  %2d platforms  lines %s", stop.name.take(30), departures.size, realtime,
                departures.count { it.platform != null }, departures.map { it.line.name }.distinct().take(12)))
            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            for (d in departures) {
                belgianNameProblem(d.headsign)?.let { problems += "${stop.name}: ${d.line.name} → '${d.headsign}': $it" }
                belgianNameProblem(d.stopName)?.let { problems += "${stop.name}: stop '${d.stopName}': $it" }
                if (d.tripId.contains("_be-sncb_") && Regex("""^[A-Z]+ \d+$""").matches(d.line.name)) problems += "${stop.name}: train named '${d.line.name}'"
                if (d.operator == "LETEC") problems += "${stop.name}: operator LETEC"
            }
        }
        assertTrue("no town lookups in Belgium", townLookups.get() == 0)
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `belgian train trip opens with readable stops`() = runBlocking {
        val stop = germanReader.searchStops("Brussel-Zuid", near = null).first()
        val departure = germanReader.departures(stop, count = 40).departures
            .firstOrNull { it.line.name == "IC" && !it.cancelled }
        assumeTrue("no Intercity right now", departure != null)
        departure!!
        val trip = germanReader.trip(departure.tripId, contextStopId = departure.stopId)
        println("${trip.line.name} ${trip.tripNumber} → ${trip.headsign} (real-time ${trip.isRealtime}): " +
            trip.stops.joinToString(" – ") { "${it.name} (${it.platform})" })
        assertTrue("too few stops", trip.stops.size >= 2)
        assertTrue("origin stop not found", indexOfStop(trip.stops, departure.stopId, departure.stopName) >= 0)
        val bad = trip.stops.mapNotNull { s -> belgianNameProblem(s.name)?.let { "${s.name}: $it" } }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `nearby stops around the Grand-Place in Brussels`() = runBlocking {
        val nearby = germanReader.nearbyStops(LatLon(50.8467, 4.3525))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.isNotEmpty())
        assertTrue(nearby.none { belgianNameProblem(it.stop.name) != null })
    }

    @Test
    fun `italian departure boards read the Italian way and need no town lookups`() = runBlocking {
        val problems = mutableListOf<String>()
        val stations = listOf(
            "Roma Termini", "Milano Centrale", "Napoli Centrale", "Torino Porta Nuova", "Bologna Centrale",
            "Venezia Santa Lucia", "Milano Cadorna", "Duomo M3", "Bolzano, NOI",
        )
        // Four capitals in a row: a name the rules missed (abbreviations like "FS" or "GTT" are shorter).
        val shouting = Regex("""\p{Lu}{4,}""")
        for (query in stations) {
            val stop = repository.searchStops(query, near = null).firstOrNull()
            if (stop == null || stop.country() != Italy) {
                problems += "$query: found ${stop?.name} (${stop?.id}, ${stop?.country()})"
                continue
            }
            val before = townLookups.get()
            val departures = repository.departures(stop, count = 30).departures
            val lookups = townLookups.get() - before
            val realtime = if (departures.isEmpty()) 0 else departures.count { it.isRealtime } * 100 / departures.size
            println(String.format("%-36s %3d departures  %3d%% real-time  %2d lookups  lines %s", stop.name.take(36), departures.size, realtime,
                lookups, departures.map { it.line.name }.distinct().take(14)))
            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (shouting.containsMatchIn(stop.name)) problems += "stop name '${stop.name}'"
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            // Only destinations abroad (FlixBus to Lyon from Napoli Centrale) may need one.
            val abroad = departures.mapNotNull { it.destination }.filter { it.country() != Italy }.distinctBy { it.name }
            if (lookups > abroad.size) problems += "${stop.name}: $lookups town lookups for ${abroad.map { it.name }}"
            departures.filter { it.headsign.isBlank() }.forEach { problems += "${stop.name}: ${it.line.name} has no destination" }
            departures.filter { shouting.containsMatchIn(it.headsign) && it.destination?.country() == Italy }
                .forEach { problems += "${stop.name}: ${it.line.name} → '${it.headsign}' (${it.tripId})" }
            // The same train twice at the same time (Trenitalia's outdated copies). The timetable also has trains
            // twice at different times, both current (IC 503 to Savona at 04:40 and 06:05 on 2026-11-27): left as they are.
            departures.filter { it.line.mode.isRail && it.tripNumber != null }.groupBy { Triple(it.tripNumber, it.headsign, it.scheduled) }
                .filter { it.value.size > 1 }
                .forEach { (key, same) -> problems += "${stop.name}: train ${key.first} shown ${same.size} times (${same.map { it.line.name }})" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `italian train trip opens with its stops`() = runBlocking {
        val stop = repository.searchStops("Roma Termini", near = null).first()
        val departure = repository.departures(stop, count = 40).departures
            .firstOrNull { it.line.mode == TransportMode.LONG_DISTANCE && !it.cancelled }
        assumeTrue("no long-distance train right now", departure != null)
        departure!!
        val trip = repository.trip(departure.tripId, contextStopId = departure.stopId)
        println("${trip.line.name} ${trip.tripNumber} → ${trip.headsign}: " + trip.stops.joinToString(" – ") { it.name })
        assertTrue("too few stops", trip.stops.size >= 2)
        assertTrue("origin stop not found", indexOfStop(trip.stops, departure.stopId, departure.stopName) >= 0)
        assertTrue("stop names in capitals", trip.stops.none { it.name == it.name.uppercase() })
    }

    @Test
    fun `nearby stops around Milan cathedral`() = runBlocking {
        val nearby = repository.nearbyStops(LatLon(45.4642, 9.1900))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.any { it.stop.name.startsWith("Duomo") })
    }

    @Test
    fun `luxembourg boards show every train once and need no town lookups`() = runBlocking {
        val problems = mutableListOf<String>()
        for (query in listOf("Luxembourg Gare", "Esch-sur-Alzette, Gare", "Ettelbruck, Gare", "Hamilius", "Kirchberg, Luxexpo", "Wasserbillig, Gare")) {
            val stop = repository.searchStops(query, near = null).firstOrNull()
            if (stop == null || stop.country() != Luxembourg) {
                problems += "$query: found ${stop?.name} (${stop?.id}, ${stop?.country()})"
                continue
            }
            val before = townLookups.get()
            val departures = repository.departures(stop, count = 40).departures
            val lookups = townLookups.get() - before
            println(String.format("%-36s %3d departures  %2d real-time  %2d lookups  lines %s", stop.name.take(36), departures.size,
                departures.count { it.isRealtime }, lookups, departures.map { it.line.name }.distinct().take(14)))
            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            val abroad = departures.mapNotNull { it.destination }.filter { it.country() != Luxembourg }.distinctBy { it.name }
            if (lookups > abroad.size) problems += "${stop.name}: $lookups town lookups for ${abroad.map { it.name }}"
            departures.filter { it.headsign.isBlank() }.forEach { problems += "${stop.name}: ${it.line.name} has no destination" }
            // A train in several feeds is shown once (numbers 2n and 2n+1 are one French train).
            departures.filter { it.line.mode.isRail && it.tripNumber?.all(Char::isDigit) == true }
                .groupBy { it.scheduled to it.tripNumber!!.trimStart('0').toLong() / 2 }
                .filterValues { it.size > 1 }
                .forEach { (_, same) -> problems += "${stop.name}: twice " + same.joinToString { "${it.line.name} ${it.tripNumber} → ${it.headsign}" } }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `nearby stops around Amsterdam Dam`() = runBlocking {
        val nearby = repository.nearbyStops(LatLon(52.3731, 4.8926))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.any { it.stop.name == "Amsterdam, Dam" })
    }

    @Test
    fun `nearby stops around Cologne cathedral`() = runBlocking {
        val nearby = repository.nearbyStops(LatLon(50.9420, 6.9580))
        println("nearby: " + nearby.joinToString { "${it.stop.name} ${it.distanceMeters} m" })
        assertTrue(nearby.any { it.stop.name.contains("Dom") || it.stop.name.contains("Hbf") })
    }

    @Test
    fun `french departure boards read like the station boards`() = runBlocking {
        val problems = mutableListOf<String>()
        val french = TransitRepository(TransitousApi(userAgent = "Abfahrten-tests/0.1"), userLanguage = { "fr" })
        val stations = listOf(
            "Paris Gare de Lyon", "Châtelet", "Paris Gare du Nord", "Lyon Part Dieu", "Marseille Saint-Charles",
            "Toulouse Matabiau", "Bordeaux Saint-Jean", "Lille Flandres", "Strasbourg", "Nantes Commerce", "Reims Opéra",
        )
        val shouting = Regex("""\p{Lu}{4,}""")
        for (query in stations) {
            val stop = french.searchStops(query, near = null).firstOrNull { it.country() == France }
            if (stop == null) {
                problems += "$query: not found"
                continue
            }
            val departures = french.departures(stop, count = 30).departures
            val realtime = if (departures.isEmpty()) 0 else departures.count { it.isRealtime } * 100 / departures.size
            println(String.format("%-30s %3d departures  %3d%% real-time  lines %s", stop.name.take(30), departures.size, realtime,
                departures.map { it.line.name }.distinct().take(12)))
            println("    " + departures.map { it.headsign }.distinct().take(12).joinToString(" | "))
            if (departures.isEmpty()) problems += "${stop.name}: no departures"
            for (d in departures) {
                if (d.headsign.isBlank()) problems += "${stop.name}: ${d.line.name} without destination"
                if (Regex("""^(?:[A-Z]{4}|\d+)$""").matches(d.headsign)) problems += "${stop.name}: ${d.line.name} → mission code ${d.headsign}"
                if (shouting.containsMatchIn(d.headsign.replace(Regex("""\b(?:SNCF|RER|EHPAD|ENSAM|ASPTT|CPAM|AFPA|INSA)\b"""), ""))) problems += "${stop.name}: '${d.headsign}' in capitals"
                if ("Hall" in d.headsign) problems += "${stop.name}: '${d.headsign}'"
                if (d.line.name.length > 9 && "_fr-" in d.tripId) problems += "${stop.name}: line '${d.line.name}'"
            }
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }
}
