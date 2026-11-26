package io.github.dnicotra.ovinfode.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
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

/**
 * Opens a stop's departure board. Goes back instead when that board is the screen below
 * (stop → trip → same stop), and does nothing when it is already shown.
 */
private fun NavController.openStop(stop: Stop) {
    val current = currentBackStackEntry ?: return
    if (current.lifecycle.currentState != Lifecycle.State.RESUMED) return
    fun NavBackStackEntry.showsStop() = destination.hasRoute<DeparturesRoute>() && toRoute<DeparturesRoute>().id == stop.id
    when {
        current.showsStop() -> Unit
        previousBackStackEntry?.showsStop() == true -> popBackStack()
        else -> navigate(stop.toRoute())
    }
}

private fun NavController.backSafely() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val transitions = remember(rtl) { ScreenTransitions(rtl) }
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
        predictivePopEnterTransition = transitions.predictivePopEnter,
        predictivePopExitTransition = transitions.predictivePopExit,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container) },
                onOpenStop = { nav.openStop(it) },
                onSearch = { nav.navigateSafely(SearchRoute) },
                onAbout = { nav.navigateSafely(AboutRoute) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                viewModel = viewModel { SearchViewModel(container) },
                onBack = { nav.backSafely() },
                onOpenStop = { nav.openStop(it) },
            )
        }
        composable<DeparturesRoute> { entry ->
            val stop = entry.toRoute<DeparturesRoute>().toStop()
            DeparturesScreen(
                viewModel = viewModel { DeparturesViewModel(container, stop) },
                onBack = { nav.backSafely() },
                onOpenTrip = { nav.navigateSafely(it.toTripRoute()) },
                onOpenStop = { nav.openStop(it) },
            )
        }
        composable<TripRoute> { entry ->
            val route = entry.toRoute<TripRoute>()
            TripScreen(
                viewModel = viewModel { TripViewModel(container, route.tripId, route.fromStopId, route.fromStopName) },
                onBack = { nav.backSafely() },
                onOpenStop = { nav.openStop(it) },
            )
        }
        composable<AboutRoute> {
            AboutScreen(onBack = { nav.backSafely() })
        }
    }
}
