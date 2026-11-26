package io.github.dnicotra.ovinfode.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.safeCall
import io.github.dnicotra.ovinfode.data.toErrorKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

enum class HomeTab { NEARBY, FAVORITES }

sealed interface PreviewState {
    data object Loading : PreviewState
    data class Loaded(val departures: List<Departure>) : PreviewState
    data class Failed(val kind: ErrorKind) : PreviewState
}

/** A stop card on the home screen, optionally with its next departures. */
data class StopPreview(
    val stop: Stop,
    val distanceMeters: Int? = null,
    /** null: no departures requested for this card (far-away nearby stops). */
    val preview: PreviewState? = null,
)

sealed interface NearbyState {
    data object NoPermission : NearbyState
    data object Locating : NearbyState
    data object LocationUnavailable : NearbyState
    data object Loading : NearbyState
    data class Error(val kind: ErrorKind) : NearbyState
    data class Loaded(val stops: List<StopPreview>, val approximateLocation: Boolean) : NearbyState
}

data class HomeUiState(
    /** null until the favourites are loaded and the start tab is known. */
    val tab: HomeTab? = null,
    val nearby: NearbyState = NearbyState.Locating,
    val favorites: List<StopPreview> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val refreshing: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val location = container.location

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var nearbyJob: Job? = null
    private var favoritesJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var nearbyUpdated: Instant? = null
    private var favoritesUpdated: Instant? = null

    init {
        viewModelScope.launch {
            container.prefs.favorites.collect { stops ->
                val firstEmission = _state.value.tab == null
                _state.update { s ->
                    val existing = s.favorites.associateBy { it.stop.id }
                    s.copy(
                        tab = s.tab ?: if (stops.isNotEmpty()) HomeTab.FAVORITES else HomeTab.NEARBY,
                        favorites = stops.map { stop -> existing[stop.id]?.copy(stop = stop) ?: StopPreview(stop, preview = PreviewState.Loading) },
                        favoriteIds = stops.map { it.id }.toSet(),
                    )
                }
                if (firstEmission) {
                    ensureFresh()
                } else {
                    // Fetch departures only for newly added favourites.
                    val missing = _state.value.favorites.filter { it.preview == PreviewState.Loading }.map { it.stop }
                    if (missing.isNotEmpty()) viewModelScope.launch { fetchPreviews(missing, ::applyFavoritePreview) }
                }
            }
        }
    }

    fun selectTab(tab: HomeTab) {
        _state.update { it.copy(tab = tab) }
        ensureFresh()
    }

    fun refresh() {
        when (_state.value.tab) {
            HomeTab.NEARBY -> loadNearby(userInitiated = true)
            HomeTab.FAVORITES -> loadFavorites(userInitiated = true)
            null -> Unit
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) loadNearby(userInitiated = false) else _state.update { it.copy(nearby = NearbyState.NoPermission) }
    }

    fun toggleFavorite(stop: Stop) {
        viewModelScope.launch { container.prefs.toggleFavorite(stop) }
    }

    fun onResume() {
        ensureFresh()
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_SECONDS * 1000)
                ensureFresh()
            }
        }
    }

    fun onPause() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun ensureFresh() {
        val s = _state.value
        when (s.tab) {
            HomeTab.NEARBY -> {
                val permissionChanged = (s.nearby == NearbyState.NoPermission) == location.hasPermission()
                if (permissionChanged || isStale(nearbyUpdated)) loadNearby(userInitiated = false)
            }
            HomeTab.FAVORITES -> if (isStale(favoritesUpdated)) loadFavorites(userInitiated = false)
            null -> Unit
        }
    }

    private fun isStale(updated: Instant?) =
        updated == null || Duration.between(updated, Instant.now()).seconds >= AUTO_REFRESH_SECONDS - 5

    private fun loadNearby(userInitiated: Boolean) {
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            if (!location.hasPermission()) {
                _state.update { it.copy(nearby = NearbyState.NoPermission, refreshing = false) }
                return@launch
            }
            val previous = _state.value.nearby as? NearbyState.Loaded
            if (userInitiated) _state.update { it.copy(refreshing = true) }
            if (previous == null) _state.update { it.copy(nearby = NearbyState.Locating) }

            val here = location.currentLocation()
            if (here == null) {
                _state.update { it.copy(refreshing = false, nearby = previous ?: NearbyState.LocationUnavailable) }
                return@launch
            }
            if (previous == null) _state.update { it.copy(nearby = NearbyState.Loading) }

            safeCall { repository.nearbyStops(here) }
                .onSuccess { stops ->
                    val old = previous?.stops.orEmpty().associateBy { it.stop.id }
                    val previews = stops.mapIndexed { index, nearby ->
                        StopPreview(
                            stop = nearby.stop,
                            distanceMeters = nearby.distanceMeters,
                            preview = if (index < NEARBY_PREVIEWS) old[nearby.stop.id]?.preview ?: PreviewState.Loading else null,
                        )
                    }
                    _state.update { it.copy(nearby = NearbyState.Loaded(previews, location.isApproximateOnly())) }
                    nearbyUpdated = Instant.now()
                    fetchPreviews(previews.take(NEARBY_PREVIEWS).map { it.stop }, ::applyNearbyPreview)
                }
                .onFailure { e ->
                    if (previous == null) _state.update { it.copy(nearby = NearbyState.Error(e.toErrorKind())) }
                }
            _state.update { it.copy(refreshing = false) }
        }
    }

    private fun loadFavorites(userInitiated: Boolean) {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            if (userInitiated) _state.update { it.copy(refreshing = true) }
            favoritesUpdated = Instant.now()
            fetchPreviews(_state.value.favorites.take(MAX_FAVORITE_PREVIEWS).map { it.stop }, ::applyFavoritePreview)
            _state.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun fetchPreviews(stops: List<Stop>, apply: (String, PreviewState) -> Unit) = coroutineScope {
        stops.map { stop ->
            async {
                val result = safeCall { repository.departures(stop, PREVIEW_COUNT) }
                apply(stop.id, result.fold({ PreviewState.Loaded(it.departures) }, { PreviewState.Failed(it.toErrorKind()) }))
            }
        }.awaitAll()
    }

    /** A failed refresh keeps the departures that are already shown. */
    private fun merge(old: PreviewState?, new: PreviewState): PreviewState =
        if (new is PreviewState.Failed && old is PreviewState.Loaded) old else new

    private fun applyNearbyPreview(stopId: String, preview: PreviewState) = _state.update { s ->
        val nearby = s.nearby as? NearbyState.Loaded ?: return@update s
        s.copy(nearby = nearby.copy(stops = nearby.stops.map { if (it.stop.id == stopId) it.copy(preview = merge(it.preview, preview)) else it }))
    }

    private fun applyFavoritePreview(stopId: String, preview: PreviewState) = _state.update { s ->
        s.copy(favorites = s.favorites.map { if (it.stop.id == stopId) it.copy(preview = merge(it.preview, preview)) else it })
    }

    private companion object {
        const val NEARBY_PREVIEWS = 6
        const val MAX_FAVORITE_PREVIEWS = 12
        const val PREVIEW_COUNT = 8
        const val AUTO_REFRESH_SECONDS = 90L
    }
}
