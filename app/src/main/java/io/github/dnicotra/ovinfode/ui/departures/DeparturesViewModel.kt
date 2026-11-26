package io.github.dnicotra.ovinfode.ui.departures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.country.country
import io.github.dnicotra.ovinfode.data.DepartureViewMode
import io.github.dnicotra.ovinfode.data.model.Alert
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.data.mergeSameTrains
import io.github.dnicotra.ovinfode.data.safeCall
import io.github.dnicotra.ovinfode.data.toErrorKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class DeparturesUiState(
    val stop: Stop,
    /** First load, nothing to show yet. */
    val loading: Boolean = true,
    /** User pulled to refresh. */
    val refreshing: Boolean = false,
    /** Error of the first load (no data to show). */
    val error: ErrorKind? = null,
    /** A background refresh failed; [departures] are from [lastUpdated]. */
    val refreshFailed: Boolean = false,
    val departures: List<Departure> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val nextPageCursor: String? = null,
    val loadingMore: Boolean = false,
    val filter: TransportMode? = null,
    val lastUpdated: Instant? = null,
) {
    val availableModes: List<TransportMode> get() = departures.map { it.line.mode }.distinct().sorted()
    val visibleDepartures: List<Departure>
        get() = if (filter == null) departures else departures.filter { it.line.mode == filter }

    /** Say that times are from the timetable where the country has no real-time data at all (Luxembourg). */
    val timetableOnly: Boolean
        get() = departures.isNotEmpty() && departures.none { it.isRealtime } && !stop.country().hasRealtimeData
}

/**
 * Stops opened from a trip come without their transport modes: take them from the departures,
 * so a favourite saved from here still shows its mode icons.
 */
internal fun Stop.withModesFrom(departures: List<Departure>): Stop =
    if (modes.isEmpty() && departures.isNotEmpty()) copy(modes = departures.map { it.line.mode }.toSortedSet()) else this

class DeparturesViewModel(
    private val container: AppContainer,
    stop: Stop,
) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(DeparturesUiState(stop = stop))
    val state: StateFlow<DeparturesUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = container.prefs.isFavorite(stop.id)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val viewMode: StateFlow<DepartureViewMode> = container.prefs.viewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, DepartureViewMode.BY_TIME)

    private var loadJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        load(userInitiated = false)
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val current = _state.value
            if (userInitiated && current.departures.isNotEmpty()) _state.update { it.copy(refreshing = true) }
            if (current.departures.isEmpty()) _state.update { it.copy(loading = true, error = null) }
            // Keep the list about as long as it is, so "later departures" are not lost on refresh.
            val count = maxOf(PAGE_SIZE, current.departures.size)
            safeCall { repository.departures(current.stop, count) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            stop = it.stop.withModesFrom(result.departures),
                            loading = false,
                            refreshing = false,
                            error = null,
                            refreshFailed = false,
                            departures = result.departures,
                            alerts = result.alerts,
                            nextPageCursor = result.nextPageCursor,
                            lastUpdated = Instant.now(),
                            filter = it.filter?.takeIf { f -> result.departures.any { d -> d.line.mode == f } },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.departures.isEmpty()) {
                            it.copy(loading = false, refreshing = false, error = e.toErrorKind())
                        } else {
                            it.copy(loading = false, refreshing = false, refreshFailed = true)
                        }
                    }
                }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextPageCursor ?: return
        if (_state.value.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            safeCall { repository.departures(_state.value.stop, PAGE_SIZE, cursor) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            // A train in two feeds can straddle the pages.
                            departures = mergeSameTrains(
                                (it.departures + result.departures).distinctBy { d -> Triple(d.tripId, d.line.name, d.scheduled) },
                                it.stop.location,
                            ),
                            nextPageCursor = result.nextPageCursor,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun setFilter(mode: TransportMode?) = _state.update { it.copy(filter = mode) }

    fun setViewMode(mode: DepartureViewMode) {
        viewModelScope.launch { container.prefs.setViewMode(mode) }
    }

    fun toggleFavorite() {
        viewModelScope.launch { container.prefs.toggleFavorite(_state.value.stop) }
    }

    /** Called while the screen is visible: refresh now if stale, then every 30 s. */
    fun onResume() {
        val last = _state.value.lastUpdated
        if (last != null && Duration.between(last, Instant.now()).seconds >= AUTO_REFRESH_SECONDS - 5) load(userInitiated = false)
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_SECONDS * 1000)
                load(userInitiated = false)
            }
        }
    }

    fun onPause() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val AUTO_REFRESH_SECONDS = 30L
    }
}
