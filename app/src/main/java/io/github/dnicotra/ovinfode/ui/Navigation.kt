package io.github.dnicotra.ovinfode.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.ui.about.AboutScreen
import io.github.dnicotra.ovinfode.ui.departures.DeparturesScreen
import io.github.dnicotra.ovinfode.ui.departures.DeparturesViewModel
import io.github.dnicotra.ovinfode.ui.home.HomeScreen
import io.github.dnicotra.ovinfode.ui.home.HomeViewModel
import io.github.dnicotra.ovinfode.ui.search.SearchScreen
import io.github.dnicotra.ovinfode.ui.search.SearchViewModel
import io.github.dnicotra.ovinfode.ui.trip.TripScreen
import io.github.dnicotra.ovinfode.ui.trip.TripViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SearchRoute

@Serializable
object AboutRoute

@Serializable
data class DeparturesRoute(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val area: String? = null,
    val modes: List<String> = emptyList(),
) {
    fun toStop() = Stop(id, name, lat, lon, area, modes.mapNotNull { runCatching { TransportMode.valueOf(it) }.getOrNull() }.toSortedSet())
}

fun Stop.toRoute() = DeparturesRoute(id, name, lat, lon, area, modes.map { it.name })

@Serializable
data class TripRoute(
    val tripId: String,
    val fromStopId: String? = null,
    val fromStopName: String? = null,
)

fun Departure.toTripRoute() = TripRoute(tripId, stopId, stopName)

/** Ignores taps while a screen transition is still running (prevents double navigation). */
private fun NavController.navigateSafely(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

private fun NavController.backSafely() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

/*
 * Material "shared axis X" motion: screens slide a short distance horizontally while the old one
 * fades out quickly and the new one fades in afterwards, so the two never show through each other.
 * Back is the exact mirror of forward, which also makes the predictive back gesture look natural.
 */
private const val MOTION_MILLIS = 300
private const val FADE_OUT_MILLIS = 90
private val SLIDE_DISTANCE = 30.dp

private fun sharedAxisEnter(offsetPx: Int): EnterTransition =
    slideInHorizontally(tween(MOTION_MILLIS, easing = FastOutSlowInEasing)) { offsetPx } +
        fadeIn(tween(MOTION_MILLIS - FADE_OUT_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))

private fun sharedAxisExit(offsetPx: Int): ExitTransition =
    slideOutHorizontally(tween(MOTION_MILLIS, easing = FastOutSlowInEasing)) { offsetPx } +
        fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val slide = with(LocalDensity.current) { SLIDE_DISTANCE.roundToPx() }
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { sharedAxisEnter(slide) },
        exitTransition = { sharedAxisExit(-slide) },
        popEnterTransition = { sharedAxisEnter(-slide) },
        popExitTransition = { sharedAxisExit(slide) },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container) },
                onOpenStop = { nav.navigateSafely(it.toRoute()) },
                onSearch = { nav.navigateSafely(SearchRoute) },
                onAbout = { nav.navigateSafely(AboutRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                viewModel = viewModel { SearchViewModel(container) },
                onBack = { nav.backSafely() },
                onOpenStop = { nav.navigateSafely(it.toRoute()) },
            )
        }
        composable<DeparturesRoute> { entry ->
            val stop = entry.toRoute<DeparturesRoute>().toStop()
            DeparturesScreen(
                viewModel = viewModel { DeparturesViewModel(container, stop) },
                onBack = { nav.backSafely() },
                onOpenTrip = { nav.navigateSafely(it.toTripRoute()) },
            )
        }
        composable<TripRoute> { entry ->
            val route = entry.toRoute<TripRoute>()
            TripScreen(
                viewModel = viewModel { TripViewModel(container, route.tripId, route.fromStopId, route.fromStopName) },
                onBack = { nav.backSafely() },
            )
        }
        composable<AboutRoute> {
            AboutScreen(onBack = { nav.backSafely() })
        }
    }
}
