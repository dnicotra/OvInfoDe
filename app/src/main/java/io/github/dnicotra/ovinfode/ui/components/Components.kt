package io.github.dnicotra.ovinfode.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.country.Country
import io.github.dnicotra.ovinfode.country.OtherCountry
import io.github.dnicotra.ovinfode.data.model.Alert
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.TransportMode

/**
 * Colours for lines whose feed has none, loosely following German signage conventions.
 * Countries can set their own in [Country.line] (Dutch trains are NS yellow).
 */
private fun defaultLineColor(mode: TransportMode): Color = when (mode) {
    TransportMode.LONG_DISTANCE -> Color(0xFFEC0016)
    TransportMode.REGIONAL -> Color(0xFF646973)
    TransportMode.SUBURBAN -> Color(0xFF408335)
    TransportMode.SUBWAY -> Color(0xFF1455C0)
    TransportMode.TRAM -> Color(0xFFC0392B)
    TransportMode.BUS -> Color(0xFF7B3F99)
    TransportMode.COACH -> Color(0xFF2E7D32)
    TransportMode.FERRY -> Color(0xFF00838F)
    TransportMode.CABLE -> Color(0xFF6D4C41)
    TransportMode.ON_DEMAND -> Color(0xFFEF6C00)
    TransportMode.OTHER -> Color(0xFF546E7A)
}

@Composable
fun LineBadge(line: Line, modifier: Modifier = Modifier, compact: Boolean = false) {
    val background = line.color?.let { Color(it) } ?: defaultLineColor(line.mode)
    val text = line.textColor?.let { Color(it) }
        ?: if (background.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier
            .widthIn(min = if (compact) 40.dp else 52.dp)
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Long names such as "ICE 1234" or "RE 10a" shrink instead of being cut off.
        val base = if (compact) 13f else 15f
        val scale = when {
            line.name.length <= 4 -> 1f
            line.name.length <= 6 -> 0.87f
            else -> 0.74f
        }
        Text(
            text = line.name,
            color = text,
            fontWeight = FontWeight.Bold,
            fontSize = (base * scale).sp,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

fun TransportMode.icon(): ImageVector = when (this) {
    TransportMode.LONG_DISTANCE, TransportMode.REGIONAL -> Icons.Filled.Train
    TransportMode.SUBURBAN -> Icons.Filled.DirectionsRailway
    TransportMode.SUBWAY -> Icons.Filled.Subway
    TransportMode.TRAM -> Icons.Filled.Tram
    TransportMode.BUS -> Icons.Filled.DirectionsBus
    TransportMode.COACH -> Icons.Filled.AirportShuttle
    TransportMode.FERRY -> Icons.Filled.DirectionsBoat
    TransportMode.ON_DEMAND -> Icons.Filled.LocalTaxi
    TransportMode.CABLE, TransportMode.OTHER -> Icons.Filled.Commute
}

/** Name of the mode as used in [country] ("U-Bahn" in Germany, "Metro" elsewhere). */
@StringRes
fun TransportMode.labelRes(country: Country = OtherCountry): Int = country.modeLabel(this) ?: when (this) {
    TransportMode.LONG_DISTANCE -> R.string.mode_long_distance
    TransportMode.REGIONAL -> R.string.mode_regional
    TransportMode.SUBURBAN -> R.string.mode_suburban
    TransportMode.SUBWAY -> R.string.mode_subway
    TransportMode.TRAM -> R.string.mode_tram
    TransportMode.BUS -> R.string.mode_bus
    TransportMode.COACH -> R.string.mode_coach
    TransportMode.FERRY -> R.string.mode_ferry
    TransportMode.CABLE -> R.string.mode_cable
    TransportMode.ON_DEMAND -> R.string.mode_on_demand
    TransportMode.OTHER -> R.string.mode_other
}

/** Small row of mode icons, e.g. for search results and nearby stops; [country] names them for accessibility. */
@Composable
fun ModeIcons(modes: Collection<TransportMode>, country: Country, modifier: Modifier = Modifier) {
    // Long-distance and regional share the train icon.
    val icons = modes.sorted().map { it.icon() }.distinct()
    val label = modes.sorted().map { stringResource(it.labelRes(country)) }.distinct().joinToString()
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icons.forEach { icon ->
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@StringRes
fun ErrorKind.messageRes(): Int = when (this) {
    ErrorKind.NETWORK -> R.string.error_network
    ErrorKind.SERVER -> R.string.error_server
    ErrorKind.NOT_FOUND -> R.string.error_not_found
    ErrorKind.UNKNOWN -> R.string.error_unknown
}

@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorMessage(kind: ErrorKind, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredMessage(
        text = stringResource(kind.messageRes()),
        icon = Icons.Filled.Warning,
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Collapsible card with service alerts ("Meldungen"). */
@Composable
fun AlertsCard(alerts: List<Alert>, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (expanded || alerts.size > 1) pluralStringResource(R.plurals.alerts_count, alerts.size, alerts.size) else alerts.first().header.ifBlank { alerts.first().description },
                style = MaterialTheme.typography.titleSmall,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                alerts.forEach { alert ->
                    Column {
                        if (alert.header.isNotBlank()) Text(alert.header, style = MaterialTheme.typography.titleSmall)
                        if (alert.description.isNotBlank() && alert.description != alert.header) {
                            Text(alert.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        alert.url?.let { url ->
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttributionFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

@Composable
fun SectionSpacer() = Spacer(Modifier.height(8.dp))
