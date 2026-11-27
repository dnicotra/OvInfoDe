package io.github.dnicotra.ovinfode.ui

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.dnicotra.ovinfode.FakeTransitous
import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.DepartureViewMode
import io.github.dnicotra.ovinfode.data.TransitRepository
import io.github.dnicotra.ovinfode.data.api.MatchDto
import io.github.dnicotra.ovinfode.data.api.PlaceDto
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.Alert
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.data.toStop
import io.github.dnicotra.ovinfode.ui.departures.DeparturesContent
import io.github.dnicotra.ovinfode.ui.departures.DeparturesUiState
import io.github.dnicotra.ovinfode.ui.home.HomeContent
import io.github.dnicotra.ovinfode.ui.home.HomeTab
import io.github.dnicotra.ovinfode.ui.home.HomeUiState
import io.github.dnicotra.ovinfode.ui.home.NearbyState
import io.github.dnicotra.ovinfode.ui.home.PreviewState
import io.github.dnicotra.ovinfode.ui.home.StopPreview
import io.github.dnicotra.ovinfode.ui.search.SearchContent
import io.github.dnicotra.ovinfode.ui.search.SearchResult
import io.github.dnicotra.ovinfode.ui.search.SearchUiState
import io.github.dnicotra.ovinfode.ui.theme.AppTheme
import io.github.dnicotra.ovinfode.ui.trip.TripContent
import io.github.dnicotra.ovinfode.ui.trip.TripUiState
import io.github.dnicotra.ovinfode.util.distanceMeters
import io.github.dnicotra.ovinfode.util.indexOfStop
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/**
 * Renders the screens with recorded API data. Record with `./gradlew recordRoborazziDebug`;
 * the images land in docs/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @Before
    fun berlinTime() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("../docs/screenshots/$name.png") {
            AppTheme(darkTheme = dark, dynamicColor = false) { content() }
        }

    private val koelnStop = Stop(
        "at-Railway-Current-Reference-Data-2027_de:05315:11201:7:72", "Köln Hbf", 50.9430, 6.9589, "Köln",
        sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.REGIONAL, TransportMode.SUBURBAN, TransportMode.TRAM, TransportMode.BUS),
    )
    private val koelnNow = Instant.parse("2026-11-26T06:08:40Z")

    private fun koelnBoard(filter: TransportMode? = null, alerts: List<Alert> = emptyList()) = Fixtures.koeln.let {
        DeparturesUiState(
            stop = koelnStop,
            loading = false,
            departures = it.departures,
            alerts = alerts,
            nextPageCursor = it.nextPageCursor,
            filter = filter,
            lastUpdated = koelnNow,
        )
    }

    @Composable
    private fun Board(state: DeparturesUiState, mode: DepartureViewMode, now: Instant, favorite: Boolean = true) =
        DeparturesContent(state, favorite, mode, now, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun departuresByTime() = capture("departures") { Board(koelnBoard(), DepartureViewMode.BY_TIME, koelnNow) }

    @Test
    fun departuresDark() = capture("departures_dark", dark = true) { Board(koelnBoard(), DepartureViewMode.BY_TIME, koelnNow) }

    @Test
    fun departuresFilteredWithAlert() = capture("departures_filtered_alert") {
        Board(
            koelnBoard(
                filter = TransportMode.SUBURBAN,
                alerts = listOf(Alert("Beispielmeldung: Bauarbeiten", "So sieht eine Störungsmeldung aus (Testdaten).")),
            ),
            DepartureViewMode.BY_TIME,
            koelnNow,
        )
    }

    @Test
    fun departuresByLine() {
        val berlin = Fixtures.berlin
        val stop = Stop(
            "de-DELFI_de:11000:900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.52151, 13.411267, "Berlin",
            sortedSetOf(TransportMode.REGIONAL, TransportMode.SUBURBAN, TransportMode.SUBWAY, TransportMode.BUS),
        )
        val now = Instant.parse("2026-11-26T06:04:10Z")
        capture("departures_by_line") {
            Board(DeparturesUiState(stop, loading = false, departures = berlin.departures, lastUpdated = now), DepartureViewMode.BY_LINE, now, favorite = false)
        }
    }

    @Test
    fun trip() {
        val trip = Fixtures.tripS3
        val state = TripUiState(
            loading = false,
            trip = trip,
            highlightIndex = indexOfStop(trip.stops, "de-VBB_de:11000:900100003:2:53", null),
            lastUpdated = Instant.parse("2026-11-26T06:05:30Z"),
        )
        capture("trip") { TripContent(state, Instant.parse("2026-11-26T06:05:30Z"), {}, {}) }
    }

    /** The ICE from Heidelberg to Stuttgart, whose final stop is called just "Hauptbahnhof" in the data. */
    @Test
    fun tripToStuttgartDark() {
        val fake = FakeTransitous(mapOf("/api/v6/trip" to "trip_ice1697.json"))
        val heidelbergPlatform = "de-DELFI_de:08221:1160:5:8"
        val trip = runBlocking { TransitRepository(fake.api).trip("20261126_07:03_de-DELFI_3490184955", heidelbergPlatform) }
        val now = Instant.parse("2026-11-26T07:01:00Z")
        val state = TripUiState(loading = false, trip = trip, highlightIndex = indexOfStop(trip.stops, heidelbergPlatform, null), lastUpdated = now)
        capture("trip_ice_dark", dark = true) { TripContent(state, now, {}, {}) }
    }

    @Test
    fun departuresHeidelberg() {
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_heidelberg_hbf.json"))
        val stop = Stop("at-Railway-Current-Reference-Data-2026_de:08221:1160:1:1", "Heidelberg Hbf", 49.4035, 8.6748)
        val board = runBlocking { TransitRepository(fake.api).departures(stop, 10) }
        val now = Instant.parse("2026-11-26T07:00:30Z")
        capture("departures_heidelberg") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    /** Amsterdam Centraal in Dutch: NS trains as "IC"/"SPR" in NS yellow, the metro with its line colours. */
    @Test
    @Config(qualifiers = "nl-rNL-w400dp-h860dp-xxhdpi")
    fun departuresAmsterdam() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_amsterdam_centraal.json"))
        val stop = Stop(
            "de-DELFI_000008400058", "Amsterdam Centraal", 52.37919, 4.899431, "Amsterdam",
            sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.REGIONAL, TransportMode.SUBWAY, TransportMode.TRAM, TransportMode.BUS),
        )
        val board = runBlocking { TransitRepository(fake.api).departures(stop, 30) }
        val now = Instant.parse("2026-11-26T09:12:40Z")
        capture("departures_amsterdam") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    /** Roma Termini in Italian: Trenitalia's names in capitals spelled the Italian way, each train once. */
    @Test
    @Config(qualifiers = "it-rIT-w400dp-h860dp-xxhdpi")
    fun departuresRoma() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_roma_termini.json"))
        val stop = Stop(
            "it-trenitalia_08409:station", "Roma Termini", 41.9005, 12.502, "Roma",
            sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.REGIONAL, TransportMode.BUS),
        )
        val board = runBlocking { TransitRepository(fake.api).departures(stop, 40) }
        val now = Instant.parse("2026-11-27T06:58:40Z")
        capture("departures_roma") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    /** Luxembourg station in French: CFL trains named as in Luxembourg, each cross-border train once, timetable times only. */
    @Test
    @Config(qualifiers = "fr-rLU-w400dp-h860dp-xxhdpi")
    fun departuresLuxembourg() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Luxembourg"))
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_luxembourg_gare.json"))
        val stop = Stop(
            "fr-horaires-sncf_FR::LMU:c24c10e0-fa0b-11e9-b755-b1228bd14244:", "Luxembourg", 49.5997, 6.1347, "Luxembourg",
            sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.REGIONAL, TransportMode.BUS, TransportMode.COACH),
        )
        val board = runBlocking { TransitRepository(fake.api).departures(stop, 40) }
        val now = Instant.parse("2026-11-27T07:30:40Z")
        capture("departures_luxembourg") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    /** Liège-Guillemins in French: TEC buses with their bays, SNCB trains as IC/S, towns no longer in capitals. */
    @Test
    @Config(qualifiers = "fr-rBE-w400dp-h860dp-xxhdpi")
    fun departuresLiege() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"))
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_liege_guillemins.json"))
        val stop = Stop(
            "be-sncb_S8841004", "Liège-Guillemins", 50.6246, 5.5667, "Liège",
            sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.REGIONAL, TransportMode.SUBURBAN, TransportMode.BUS),
        )
        val board = runBlocking { TransitRepository(fake.api, userLanguage = { "fr" }).departures(stop, 30) }
        val now = Instant.parse("2026-11-27T07:29:40Z")
        capture("departures_liege") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    /** Paris Gare de Lyon (the Île-de-France stop) in French: RER A and D to their final stops, metro M1 and M14, buses. */
    @Test
    @Config(qualifiers = "fr-rFR-w400dp-h860dp-xxhdpi")
    fun departuresParis() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        val fake = FakeTransitous(mapOf("/api/v6/stoptimes" to "stoptimes_paris_gare_de_lyon_idfm.json"))
        val stop = Stop(
            "fr-reseau-urbain-et-interurbain-dile-de-france-mobilites_IDFM:73626", "Gare de Lyon", 48.84457, 2.37515, "Paris",
            sortedSetOf(TransportMode.SUBURBAN, TransportMode.SUBWAY, TransportMode.BUS),
        )
        val board = runBlocking { TransitRepository(fake.api, userLanguage = { "fr" }).departures(stop, 30) }
        val now = Instant.parse("2026-11-27T05:05:20Z")
        capture("departures_paris") {
            Board(DeparturesUiState(stop, loading = false, departures = board.departures, lastUpdated = now), DepartureViewMode.BY_TIME, now)
        }
    }

    @Test
    fun homeNearby() {
        val here = LatLon(50.9420, 6.9580)
        val places = TransitousApi.json.decodeFromString<List<PlaceDto>>(Fixtures.text("mapstops_koeln.json"))
        val departures = Fixtures.koeln.departures
        val stops = places.map { it.toStop() }
            .map { stop -> stop to distanceMeters(here, stop.location).toInt() }
            .sortedBy { it.second }
            .mapIndexed { index, (stop, distance) ->
                val preview = when {
                    stop.name == "Köln Hbf" -> PreviewState.Loaded(departures.filter { it.line.mode.isRail })
                    stop.name == "Köln Dom/Hbf" -> PreviewState.Loaded(departures.filter { !it.line.mode.isRail })
                    index < 4 -> PreviewState.Loading
                    else -> null
                }
                StopPreview(stop, distance, preview)
            }
        val state = HomeUiState(
            tab = HomeTab.NEARBY,
            nearby = NearbyState.Loaded(stops, approximateLocation = false),
            favoriteIds = setOf(koelnStop.id, stops.first().stop.id),
        )
        capture("home_nearby") { HomeContent(state, koelnNow, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test
    fun homeNoPermission() = capture("home_no_permission") {
        HomeContent(HomeUiState(tab = HomeTab.NEARBY, nearby = NearbyState.NoPermission), koelnNow, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun search() {
        val berlin = LatLon(52.52, 13.405)
        val results = TransitousApi.json.decodeFromString<List<MatchDto>>(Fixtures.text("geocode_alexanderplatz.json"))
            .map { it.toStop() }
            .map { SearchResult(it, distanceMeters(berlin, it.location).toInt()) }
        capture("search") {
            SearchContent(SearchUiState(query = "Alexanderplatz", results = results, searched = true), {}, {}, {}, {}, autoFocus = false)
        }
    }
}
