package io.github.dnicotra.ovinfode.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dnicotra.ovinfode.AppContainer
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.data.safeCall
import io.github.dnicotra.ovinfode.data.toErrorKind
import io.github.dnicotra.ovinfode.util.indexOfStop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class TripUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: ErrorKind? = null,
    val refreshFailed: Boolean = false,
    val trip: Trip? = null,
    /** Index of the stop the user opened the trip from, -1 if unknown. */
    val highlightIndex: Int = -1,
    val lastUpdated: Instant? = null,
)

class TripViewModel(
    container: AppContainer,
    private val tripId: String,
    private val fromStopId: String?,
    private val fromStopName: String?,
) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        load(userInitiated = false)
    }

    fun refresh() = load(userInitiated = true)

    private fun load(userInitiated: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (userInitiated && _state.value.trip != null) _state.update { it.copy(refreshing = true) }
            if (_state.value.trip == null) _state.update { it.copy(loading = true, error = null) }
            safeCall { repository.trip(tripId) }
                .onSuccess { trip ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            refreshFailed = false,
                            trip = trip,
                            highlightIndex = indexOfStop(trip.stops, fromStopId, fromStopName),
                            lastUpdated = Instant.now(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        if (it.trip == null) it.copy(loading = false, refreshing = false, error = e.toErrorKind())
                        else it.copy(refreshing = false, refreshFailed = true)
                    }
                }
        }
    }

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
        const val AUTO_REFRESH_SECONDS = 30L
    }
}
