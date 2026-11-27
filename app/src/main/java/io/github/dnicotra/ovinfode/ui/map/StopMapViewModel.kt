package io.github.dnicotra.ovinfode.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.safeCall
import io.github.dnicotra.ovinfode.util.PlatformPlace
import io.github.dnicotra.ovinfode.util.platformPlaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the map can point at: the stop as a whole, one of its platforms, or another stop of the same place. */
sealed interface MapTarget {
    val location: LatLon

    data class WholeStop(val stop: Stop) : MapTarget {
        override val location get() = stop.location
    }

    data class Platform(val place: PlatformPlace) : MapTarget {
        override val location get() = place.location
    }

    data class OtherStop(val nearby: NearbyStop) : MapTarget {
        override val location get() = nearby.stop.location
    }
}

data class StopMapUiState(
    val stop: Stop,
    val loading: Boolean = true,
    /** Where departures leave from; a single place is the stop itself and not listed. */
    val platforms: List<PlatformPlace> = emptyList(),
    val similarStops: List<NearbyStop> = emptyList(),
    val selected: MapTarget = MapTarget.WholeStop(stop),
) {
    val targets: List<MapTarget>
        get() = listOf(MapTarget.WholeStop(stop)) + platforms.map { MapTarget.Platform(it) } + similarStops.map { MapTarget.OtherStop(it) }
}

class StopMapViewModel(container: AppContainer, stop: Stop) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(StopMapUiState(stop))
    val state: StateFlow<StopMapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val board = safeCall { repository.departuresForMap(stop) }.getOrNull()
            val places = board?.departures?.let(::platformPlaces).orEmpty()
            _state.update { it.copy(loading = false, platforms = places.takeIf { p -> p.size > 1 || p.singleOrNull()?.platforms?.isNotEmpty() == true }.orEmpty()) }
        }
        viewModelScope.launch {
            safeCall { repository.similarStops(stop) }.onSuccess { similar -> _state.update { it.copy(similarStops = similar) } }
        }
    }

    fun select(target: MapTarget) = _state.update { it.copy(selected = target) }
}
