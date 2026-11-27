@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dnicotra.ovinfode.ui.departures

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.country.Country
import io.github.dnicotra.ovinfode.country.country
import io.github.dnicotra.ovinfode.data.DepartureViewMode
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.ui.components.AlertsCard
import io.github.dnicotra.ovinfode.ui.components.AttributionFooter
import io.github.dnicotra.ovinfode.ui.components.CenteredMessage
import io.github.dnicotra.ovinfode.ui.components.DepartureRow
import io.github.dnicotra.ovinfode.ui.components.ErrorMessage
import io.github.dnicotra.ovinfode.ui.components.LineGroupRow
import io.github.dnicotra.ovinfode.ui.components.LoadingIndicator
import io.github.dnicotra.ovinfode.ui.components.clock
import io.github.dnicotra.ovinfode.ui.components.icon
import io.github.dnicotra.ovinfode.ui.components.labelRes
import io.github.dnicotra.ovinfode.ui.components.rememberNow
import io.github.dnicotra.ovinfode.ui.theme.LocalStatusColors
import io.github.dnicotra.ovinfode.util.groupByLine
import io.github.dnicotra.ovinfode.util.upcoming
import java.time.Instant

@Composable
fun DeparturesScreen(
    viewModel: DeparturesViewModel,
    onBack: () -> Unit,
    onOpenTrip: (Departure) -> Unit,
    onOpenStop: (Stop) -> Unit,
    onSwitchStop: (Stop) -> Unit = onOpenStop,
    onOpenMap: (Stop) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val now by rememberNow()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }

    DeparturesContent(
        state = state,
        isFavorite = isFavorite,
        viewMode = viewMode,
        now = now,
        onBack = onBack,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::refresh,
        onFilter = viewModel::setFilter,
        onViewMode = viewModel::setViewMode,
        onLoadMore = viewModel::loadMore,
        onOpenTrip = onOpenTrip,
        onOpenStop = onOpenStop,
        onSwitchStop = onSwitchStop,
        onOpenMap = onOpenMap,
    )
}

/** Stateless departure board, also used by the screenshot tests. */
@Composable
fun DeparturesContent(
    state: DeparturesUiState,
    isFavorite: Boolean,
    viewMode: DepartureViewMode,
    now: Instant,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (TransportMode?) -> Unit,
    onViewMode: (DepartureViewMode) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTrip: (Departure) -> Unit,
    onOpenStop: (Stop) -> Unit = {},
    onSwitchStop: (Stop) -> Unit = onOpenStop,
    onOpenMap: (Stop) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.stop.area?.takeIf { !state.stop.name.contains(it) }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { onOpenMap(state.stop) }) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.stop_on_map))
                    }
                    IconButton(onClick = onToggleFavorite) {
                        if (isFavorite) {
                            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.secondary)
                        } else {
                            Icon(Icons.Outlined.StarBorder, contentDescription = stringResource(R.string.favorite_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.departures.isEmpty() -> LoadingIndicator()
                state.error != null && state.departures.isEmpty() -> ErrorMessage(state.error, onRetry = onRefresh)
                else -> DepartureList(state, viewMode, now, onFilter, onViewMode, onLoadMore, onOpenTrip, onOpenStop, onSwitchStop)
            }
        }
    }
}

@Composable
private fun DepartureList(
    state: DeparturesUiState,
    viewMode: DepartureViewMode,
    now: Instant,
    onFilter: (TransportMode?) -> Unit,
    onViewMode: (DepartureViewMode) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTrip: (Departure) -> Unit,
    onOpenStop: (Stop) -> Unit,
    onSwitchStop: (Stop) -> Unit,
) {
    val departures = state.visibleDepartures.upcoming(now)
    // Big stations bundle several stops (e.g. the bus station next to the main station):
    // name the stop only where it differs from the one most departures use.
    val mainStopName = remember(state.departures) {
        state.departures.groupingBy { it.stopName }.eachCount().maxByOrNull { it.value }?.key
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "controls") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ViewModeToggle(viewMode, onViewMode)
                if (state.availableModes.size > 1) {
                    val country = remember(state.stop.id) { state.stop.country() }
                    ModeFilterRow(state.availableModes, country, state.filter, onFilter, Modifier.padding(top = 8.dp))
                }
                if (state.similarStops.isNotEmpty()) {
                    SimilarStopsRow(state.similarStops, onSwitchStop, Modifier.padding(top = 8.dp))
                }
            }
        }
        if (state.alerts.isNotEmpty()) {
            item(key = "alerts") { AlertsCard(state.alerts, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
        item(key = "status") { StatusLine(state) }
        if (departures.isEmpty()) {
            item(key = "empty") { CenteredMessage(stringResource(R.string.no_departures), icon = Icons.Filled.DepartureBoard) }
        } else if (viewMode == DepartureViewMode.BY_LINE) {
            val groups = departures.groupByLine()
            items(groups, key = { it.key }) { group ->
                LineGroupRow(group, now, onClick = onOpenTrip, onOpenStop = onOpenStop)
                HorizontalDivider(Modifier.padding(start = 92.dp))
            }
        } else {
            items(departures, key = { "${it.tripId}|${it.scheduled}|${it.stopId}" }) { departure ->
                DepartureRow(departure, now, onClick = { onOpenTrip(departure) }, showStopName = departure.stopName != mainStopName, onOpenStop = onOpenStop)
                HorizontalDivider(Modifier.padding(start = 92.dp))
            }
            if (state.nextPageCursor != null) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = onLoadMore) { Text(stringResource(R.string.load_more)) }
                        }
                    }
                }
            }
        }
        item(key = "footer") { AttributionFooter() }
    }
}

@Composable
private fun ViewModeToggle(viewMode: DepartureViewMode, onViewMode: (DepartureViewMode) -> Unit) {
    val options = listOf(DepartureViewMode.BY_TIME to R.string.view_time, DepartureViewMode.BY_LINE to R.string.view_line)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = viewMode == mode,
                onClick = { onViewMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun ModeFilterRow(
    modes: List<TransportMode>,
    country: Country,
    selected: TransportMode?,
    onFilter: (TransportMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onFilter(null) }, label = { Text(stringResource(R.string.filter_all)) })
        modes.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onFilter(if (selected == mode) null else mode) },
                label = { Text(stringResource(mode.labelRes(country))) },
                leadingIcon = { Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

/** The other stops of this place ("Paris Gare de Lyon" next to "Gare de Lyon"): one tap shows their board instead. */
@Composable
private fun SimilarStopsRow(stops: List<NearbyStop>, onSwitchStop: (Stop) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.similar_stops),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stops.forEach { nearby ->
                AssistChip(
                    onClick = { onSwitchStop(nearby.stop) },
                    label = { Text(stringResource(R.string.similar_stop_chip, nearby.stop.name, nearby.distanceMeters), maxLines = 1) },
                    leadingIcon = nearby.stop.modes.firstOrNull()?.let { mode ->
                        { Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: DeparturesUiState) {
    val updated = state.lastUpdated ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.refreshFailed) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = LocalStatusColors.current.delayed)
            Text(
                text = " " + stringResource(R.string.refresh_failed, updated.clock()),
                style = MaterialTheme.typography.labelSmall,
                color = LocalStatusColors.current.delayed,
            )
        } else {
            val updatedText = stringResource(R.string.updated_at, updated.clock())
            Text(
                text = if (state.timetableOnly) updatedText + " · " + stringResource(R.string.timetable_only_here) else updatedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
