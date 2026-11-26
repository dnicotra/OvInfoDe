package io.github.dnicotra.ovinfode.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.safeCall
import io.github.dnicotra.ovinfode.data.toErrorKind
import io.github.dnicotra.ovinfode.util.distanceMeters
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResult(val stop: Stop, val distanceMeters: Int?)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: ErrorKind? = null,
    /** A search for the current query has completed. */
    val searched: Boolean = false,
)

/** Results with their distance from [here], nearest first; without a location the API's order is kept. */
fun rankByDistance(stops: List<Stop>, here: LatLon?): List<SearchResult> =
    stops.map { stop -> SearchResult(stop, here?.let { distanceMeters(it, stop.location).toInt() }) }
        .sortedWith(compareBy(nullsLast()) { it.distanceMeters })

@OptIn(FlowPreview::class)
class SearchViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val location = container.location
    @Volatile private var here: LatLon? = location.lastKnown()

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        // No cached position: get a fresh one and re-sort whatever is shown by then.
        if (here == null && location.hasPermission()) {
            viewModelScope.launch {
                val fresh = location.currentLocation() ?: return@launch
                here = fresh
                _state.update { s -> s.copy(results = rankByDistance(s.results.map { it.stop }, fresh)) }
            }
        }
        viewModelScope.launch {
            _state.map { it.query.trim() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { query -> search(query) }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, searched = false, error = null) }

    fun retry() {
        viewModelScope.launch { search(_state.value.query.trim()) }
    }

    private suspend fun search(query: String) {
        if (query.length < 2) {
            _state.update { it.copy(loading = false, results = emptyList(), error = null, searched = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        safeCall { repository.searchStops(query, here) }
            .onSuccess { stops ->
                _state.update { it.copy(loading = false, results = rankByDistance(stops, here), searched = true) }
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.toErrorKind(), searched = true) } }
    }
}
