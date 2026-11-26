package io.github.dnicotra.ovinfode.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Departure
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.ui.theme.LocalStatusColors
import io.github.dnicotra.ovinfode.util.Countdown
import io.github.dnicotra.ovinfode.util.LineGroup
import io.github.dnicotra.ovinfode.util.countdown
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Instant.clock(zone: ZoneId = ZoneId.systemDefault()): String = clockFormat.format(atZone(zone))

/** The current time, updated every [periodMillis] so countdowns keep ticking between refreshes. */
@Composable
fun rememberNow(periodMillis: Long = 10_000): State<Instant> = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(periodMillis)
        value = Instant.now()
    }
}

@Composable
fun countdownText(countdown: Countdown): String = when (countdown) {
    Countdown.Now -> stringResource(R.string.now)
    is Countdown.Minutes -> stringResource(R.string.minutes_short, countdown.minutes.toInt())
    is Countdown.Clock -> countdown.time.clock()
}

@Composable
private fun countdownColor(departure: Departure): Color {
    val status = LocalStatusColors.current
    return when {
        departure.cancelled -> status.delayed
        !departure.isRealtime -> MaterialTheme.colorScheme.onSurface
        (departure.delayMinutes ?: 0) >= 1 -> status.delayed
        else -> status.onTime
    }
}

@Composable
fun platformText(platform: String, mode: TransportMode): String =
    stringResource(if (mode.isRail) R.string.platform_rail else R.string.platform_other, platform)

/** "06:05 +3" with the delay in red, or struck through when cancelled. */
@Composable
private fun scheduleLine(departure: Departure) = buildAnnotatedString {
    val status = LocalStatusColors.current
    if (departure.cancelled) {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(departure.scheduled.clock()) }
        return@buildAnnotatedString
    }
    append(departure.scheduled.clock())
    val delay = departure.delayMinutes ?: return@buildAnnotatedString
    if (delay != 0L) {
        withStyle(SpanStyle(color = if (delay > 0) status.delayed else status.onTime, fontWeight = FontWeight.SemiBold)) {
            append(if (delay > 0) " +$delay" else " $delay")
        }
    }
}

/** One departure on a departure board: line, destination, platform and countdown. */
@Composable
fun DepartureRow(
    departure: Departure,
    now: Instant,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showStopName: Boolean = false,
) {
    val status = LocalStatusColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(departure.line, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = departure.headsign,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (departure.cancelled) TextDecoration.LineThrough else null,
            )
            val details = buildAnnotatedString {
                departure.platform?.let { platform ->
                    withStyle(
                        SpanStyle(
                            color = if (departure.platformChanged) status.delayed else Color.Unspecified,
                            fontWeight = if (departure.platformChanged) FontWeight.Bold else null,
                        ),
                    ) { append(platformText(platform, departure.line.mode)) }
                }
                val extra = if (showStopName) departure.stopName else departure.operator
                if (!extra.isNullOrBlank()) {
                    if (length > 0) append(" · ")
                    append(extra)
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (departure.cancelled) stringResource(R.string.cancelled) else countdownText(countdown(departure.time, now)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = countdownColor(departure),
                textAlign = TextAlign.End,
            )
            Text(
                text = scheduleLine(departure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Single-line departure for the stop cards on the home screen. */
@Composable
fun CompactDepartureRow(departure: Departure, now: Instant, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(departure.line, compact = true, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = departure.headsign,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (departure.cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (departure.cancelled) stringResource(R.string.cancelled) else countdownText(countdown(departure.time, now)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = countdownColor(departure),
        )
    }
}

/** Departures of one line and direction in a single row: "S3 → Charlottenburg   2 · 12 · 22 min". */
@Composable
fun LineGroupRow(group: LineGroup, now: Instant, onClick: (Departure) -> Unit, modifier: Modifier = Modifier) {
    val first = group.first
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(first) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(first.line, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(first.headsign, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val platforms = group.departures.mapNotNull { it.platform }.distinct()
            if (platforms.size == 1) {
                Text(platformText(platforms.first(), first.line.mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            group.departures.take(3).forEachIndexed { index, departure ->
                Text(
                    text = if (departure.cancelled) "✕" else countdownText(countdown(departure.time, now)),
                    style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    color = countdownColor(departure),
                )
            }
        }
    }
}
