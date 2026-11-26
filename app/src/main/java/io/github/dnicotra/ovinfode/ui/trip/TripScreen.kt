@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dnicotra.ovinfode.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Trip
import io.github.dnicotra.ovinfode.data.model.TripStop
import io.github.dnicotra.ovinfode.ui.components.AlertsCard
import io.github.dnicotra.ovinfode.ui.components.AttributionFooter
import io.github.dnicotra.ovinfode.ui.components.ErrorMessage
import io.github.dnicotra.ovinfode.ui.components.LineBadge
import io.github.dnicotra.ovinfode.ui.components.LoadingIndicator
import io.github.dnicotra.ovinfode.ui.components.clock
import io.github.dnicotra.ovinfode.ui.components.icon
import io.github.dnicotra.ovinfode.ui.components.platformText
import io.github.dnicotra.ovinfode.ui.components.rememberNow
import io.github.dnicotra.ovinfode.ui.theme.LocalStatusColors
import io.github.dnicotra.ovinfode.util.VehiclePosition
import io.github.dnicotra.ovinfode.util.vehiclePosition
import java.time.Duration
import java.time.Instant

@Composable
fun TripScreen(viewModel: TripViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    TripContent(state, now, onBack = onBack, onRefresh = viewModel::refresh)
}

/** Stateless trip view, also used by the screenshot tests. */
@Composable
fun TripContent(
    state: TripUiState,
    now: Instant,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val trip = state.trip
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                title = {
                    if (trip != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(trip.line)
                            Spacer(Modifier.width(12.dp))
                            Text(trip.headsign, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                trip != null -> TripStops(trip, state.highlightIndex, now, listState)
                state.error != null -> ErrorMessage(state.error, onRetry = onRefresh)
                else -> LoadingIndicator()
            }
        }
    }
}

@Composable
private fun TripStops(trip: Trip, highlightIndex: Int, now: Instant, listState: LazyListState) {
    val position = vehiclePosition(trip.stops, now)
    val status = LocalStatusColors.current

    // Scroll to the stop the user came from, once.
    var scrolled by rememberSaveable(trip.id) { mutableStateOf(false) }
    LaunchedEffect(trip.id, highlightIndex) {
        if (!scrolled && highlightIndex > 2) listState.scrollToItem(highlightIndex - 1)
        scrolled = true
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val details = listOfNotNull(trip.operator, trip.tripNumber?.takeIf { it != trip.line.name }?.let { stringResource(R.string.trip_number, it) })
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = when {
                        trip.cancelled -> stringResource(R.string.trip_cancelled)
                        trip.isRealtime -> stringResource(R.string.realtime_data)
                        else -> stringResource(R.string.schedule_only)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        trip.cancelled -> status.delayed
                        trip.isRealtime -> status.onTime
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (position == VehiclePosition.NotStarted || position == VehiclePosition.Finished) {
                    Text(
                        stringResource(if (position == VehiclePosition.NotStarted) R.string.vehicle_not_started else R.string.vehicle_finished),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (trip.alerts.isNotEmpty()) {
            item(key = "alerts") { AlertsCard(trip.alerts, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
        trip.stops.forEachIndexed { index, stop ->
            val passed = when (position) {
                VehiclePosition.NotStarted -> false
                VehiclePosition.Finished -> true
                is VehiclePosition.AtStop -> index < position.index
                is VehiclePosition.Between -> index <= position.fromIndex
            }
            item(key = "stop$index") {
                TripStopRow(
                    stop = stop,
                    mode = trip.line.mode,
                    isFirst = index == 0,
                    isLast = index == trip.stops.lastIndex,
                    passed = passed,
                    vehicleHere = position is VehiclePosition.AtStop && position.index == index,
                    highlighted = index == highlightIndex,
                    lineColor = trip.line.color?.let { Color(it) },
                )
            }
            if (position is VehiclePosition.Between && position.fromIndex == index) {
                item(key = "vehicle") { VehicleRow(trip, lineColor = trip.line.color?.let { Color(it) }) }
            }
        }
        item(key = "footer") { AttributionFooter() }
    }
}

private val TimeColumnWidth = 64.dp
private val TrackColumnWidth = 28.dp

@Composable
private fun timelineColors(lineColor: Color?): Pair<Color, Color> {
    val active = lineColor ?: MaterialTheme.colorScheme.primary
    return active to MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun TripStopRow(
    stop: TripStop,
    mode: io.github.dnicotra.ovinfode.data.model.TransportMode,
    isFirst: Boolean,
    isLast: Boolean,
    passed: Boolean,
    vehicleHere: Boolean,
    highlighted: Boolean,
    lineColor: Color?,
) {
    val (active, inactive) = timelineColors(lineColor)
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopTimes(stop, isFirst, isLast, passed, Modifier.width(TimeColumnWidth))
        Box(
            Modifier
                .width(TrackColumnWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val stroke = 4.dp.toPx()
                if (!isFirst) drawLine(if (passed || vehicleHere) active else inactive, Offset(x, 0f), Offset(x, size.height / 2), stroke)
                if (!isLast) drawLine(if (passed) active else inactive, Offset(x, size.height / 2), Offset(x, size.height), stroke)
            }
            if (vehicleHere) {
                VehicleDot(mode, active)
            } else {
                Box(
                    Modifier
                        .size(if (isFirst || isLast || highlighted) 14.dp else 10.dp)
                        .background(if (passed) active else MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp)
                        .background(if (passed) active else inactive, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted || isFirst || isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (passed && !highlighted) dimmed else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                stop.cancelled -> Text(stringResource(R.string.stop_cancelled), style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.delayed)
                vehicleHere -> Text(stringResource(R.string.vehicle_at_stop), style = MaterialTheme.typography.bodySmall, color = active, fontWeight = FontWeight.SemiBold)
            }
        }
        stop.platform?.let { platform ->
            Text(
                text = platformText(platform, mode),
                style = MaterialTheme.typography.bodySmall,
                color = if (stop.platformChanged) LocalStatusColors.current.delayed else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (stop.platformChanged) FontWeight.Bold else null,
            )
        }
    }
}

/** Scheduled time with the real-time estimate below it (green on time, red delayed). */
@Composable
private fun StopTimes(stop: TripStop, isFirst: Boolean, isLast: Boolean, passed: Boolean, modifier: Modifier) {
    val status = LocalStatusColors.current
    // Show the departure, except at the last stop where only the arrival matters.
    val scheduled = if (isLast) stop.scheduledArrival ?: stop.scheduledDeparture else stop.scheduledDeparture ?: stop.scheduledArrival
    val estimated = if (isLast) stop.arrival ?: stop.departure else stop.departure ?: stop.arrival
    Column(modifier) {
        Text(
            text = scheduled?.clock() ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isFirst || isLast) FontWeight.Bold else FontWeight.Normal,
            color = if (passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (stop.cancelled) TextDecoration.LineThrough else null,
        )
        if (estimated != null && scheduled != null && !stop.cancelled) {
            val delay = Duration.between(scheduled, estimated).toMinutes()
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = if (delay >= 1) status.delayed else status.onTime)) {
                        append(estimated.clock())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VehicleDot(mode: io.github.dnicotra.ovinfode.data.model.TransportMode, color: Color) {
    Box(
        Modifier
            .size(24.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            mode.icon(),
            contentDescription = null,
            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Shown between two stops while the vehicle is on its way. */
@Composable
private fun VehicleRow(trip: Trip, lineColor: Color?) {
    val (active, inactive) = timelineColors(lineColor)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(TimeColumnWidth))
        Box(
            Modifier
                .width(TrackColumnWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val stroke = 4.dp.toPx()
                drawLine(active, Offset(x, 0f), Offset(x, size.height / 2), stroke)
                drawLine(inactive, Offset(x, size.height / 2), Offset(x, size.height), stroke)
            }
            VehicleDot(trip.line.mode, active)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.vehicle_between), style = MaterialTheme.typography.labelLarge, color = active, fontWeight = FontWeight.SemiBold)
    }
}
