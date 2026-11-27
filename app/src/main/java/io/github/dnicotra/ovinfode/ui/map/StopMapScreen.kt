@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.dnicotra.ovinfode.ui.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.ui.components.LineBadge
import io.github.dnicotra.ovinfode.ui.components.icon
import io.github.dnicotra.ovinfode.ui.components.platformText
import io.github.dnicotra.ovinfode.util.PlatformPlace
import io.github.dnicotra.ovinfode.util.distanceMeters
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * A stop on the map, with the platforms and bays its departures leave from where the timetable
 * places them (German stations do, platform by platform) and the other stops of the same place.
 * "Directions" hands the selected place to a maps or navigation app.
 */
@Composable
fun StopMapScreen(viewModel: StopMapViewModel, onBack: () -> Unit, onOpenStop: (Stop) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StopMapContent(state, onBack, onSelect = viewModel::select, onOpenStop = onOpenStop)
}

/** Stateless map screen, also used by the screenshot tests. */
@Composable
fun StopMapContent(state: StopMapUiState, onBack: () -> Unit, onSelect: (MapTarget) -> Unit, onOpenStop: (Stop) -> Unit) {
    val context = LocalContext.current
    val selectedName = targetName(state.selected)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { openDirections(context, state.selected.location, selectedName) }) {
                        Icon(Icons.Filled.Directions, contentDescription = stringResource(R.string.directions))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                StopMap(state, onSelect = onSelect)
                Text(
                    stringResource(R.string.map_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                )
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            TargetList(state, onSelect = onSelect, onOpenStop = onOpenStop)
        }
    }
}

@Composable
private fun targetName(target: MapTarget): String = when (target) {
    is MapTarget.WholeStop -> target.stop.name
    is MapTarget.Platform -> target.place.stopName + (target.place.platforms.firstOrNull()?.let { " · " + platformText(it, target.place.mode) } ?: "")
    is MapTarget.OtherStop -> target.nearby.stop.name
}

@Composable
private fun TargetList(state: StopMapUiState, onSelect: (MapTarget) -> Unit, onOpenStop: (Stop) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 300.dp).background(MaterialTheme.colorScheme.surface)) {
        items(state.targets) { target ->
            TargetRow(
                target = target,
                stop = state.stop,
                selected = target == state.selected,
                onClick = { onSelect(target) },
                onOpenStop = onOpenStop,
            )
            HorizontalDivider()
        }
        if (!state.loading && state.platforms.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.map_no_platforms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TargetRow(target: MapTarget, stop: Stop, selected: Boolean, onClick: () -> Unit, onOpenStop: (Stop) -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = when (target) {
            is MapTarget.WholeStop -> Icons.Filled.Place
            is MapTarget.Platform -> target.place.mode.icon()
            is MapTarget.OtherStop -> target.nearby.stop.modes.firstOrNull()?.icon() ?: Icons.Filled.Place
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            when (target) {
                is MapTarget.WholeStop -> Text(stringResource(R.string.map_whole_stop), style = MaterialTheme.typography.bodyLarge)
                is MapTarget.Platform -> PlatformText(target.place, stop)
                is MapTarget.OtherStop -> {
                    Text(stringResource(R.string.map_other_stop, target.nearby.stop.name), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${target.nearby.distanceMeters} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (target is MapTarget.OtherStop) {
            IconButton(onClick = { onOpenStop(target.nearby.stop) }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = target.nearby.stop.name)
            }
        }
    }
}

@Composable
private fun PlatformText(place: PlatformPlace, stop: Stop) {
    val title = if (place.platforms.isEmpty()) {
        place.stopName
    } else {
        place.platforms.joinToString(", ") { it }.let { platformText(it, place.mode) }
    }
    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        place.lines.take(MAX_LINES).forEach { LineBadge(it, compact = true) }
        val distance = distanceMeters(stop.location, place.location).toInt()
        if (distance >= 15) {
            Text("$distance m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_LINES = 8

/** The osmdroid map, with a marker per target; the selected one is centred and opened. */
@Composable
private fun StopMap(state: StopMapUiState, onSelect: (MapTarget) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val labels = state.targets.associateWith { markerLabel(it) }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(18.0)
            controller.setCenter(state.stop.location.toGeoPoint())
            if (hasLocationPermission(context)) {
                overlays += MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply { enableMyLocation() }
            }
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            map.overlays.removeAll { it is Marker }
            state.targets.forEach { target ->
                val marker = Marker(map).apply {
                    position = target.location.toGeoPoint()
                    title = labels[target]
                    val isSelected = target == state.selected
                    when (target) {
                        is MapTarget.WholeStop -> setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        else -> {
                            val (background, text) = labelColors(target)
                            icon = labelIcon(map.context, labels[target].orEmpty(), background, text, isSelected)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                    }
                    setOnMarkerClickListener { _, _ ->
                        onSelect(target)
                        true
                    }
                }
                map.overlays += marker
            }
            map.controller.animateTo(state.selected.location.toGeoPoint())
            map.invalidate()
        },
    )
}

/** The short text on a marker: the platform ("Gl. 4") or the lines, or the stop's name. */
@Composable
private fun markerLabel(target: MapTarget): String = when (target) {
    is MapTarget.WholeStop -> target.stop.name
    is MapTarget.Platform -> target.place.platforms.firstOrNull()?.let { first ->
        platformText(if (target.place.platforms.size > 1) "$first…" else first, target.place.mode)
    } ?: target.place.lines.take(3).joinToString(" ") { it.name }
    is MapTarget.OtherStop -> target.nearby.stop.name.let { if (it.length > 18) it.take(17) + "…" else it }
}

// Marker colours do not follow the app's theme: the map underneath is always light.
private const val PLATFORM_LABEL = 0xFF1F3A5F.toInt()
private const val OTHER_STOP_LABEL = 0xFF4A4A4A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/**
 * Background and text colour of a marker: a platform in the colour of its main line, as on
 * the line badges, where the feed has one; otherwise dark blue, other stops dark grey.
 */
internal fun labelColors(target: MapTarget): Pair<Int, Int> {
    if (target is MapTarget.Platform) {
        val line = target.place.lines.firstOrNull { it.color != null }
        val background = line?.color
        if (background != null) return background to (line.textColor ?: readableTextOn(background))
        return PLATFORM_LABEL to WHITE
    }
    return OTHER_STOP_LABEL to WHITE
}

/** White or black, whichever reads better on [background] (relative luminance, WCAG). */
internal fun readableTextOn(background: Int): Int {
    fun channel(shift: Int): Double {
        val c = ((background shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    // Contrast with white (1.05 / (L + 0.05)) against contrast with black ((L + 0.05) / 0.05).
    return if (1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05) WHITE else BLACK
}

/** A rounded label with [text] in [textColor] on [background], larger and outlined when [selected]. */
private fun labelIcon(context: Context, text: String, background: Int, textColor: Int, selected: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (selected) 15 else 13) * density
        typeface = Typeface.DEFAULT_BOLD
        color = textColor
    }
    val padH = 8 * density
    val padV = 5 * density
    // A thin outline keeps light labels apart from the map; the selected one gets a thick one.
    val border = (if (selected) 3 else 1) * density
    val width = paint.measureText(text) + 2 * padH + 2 * border
    val height = paint.fontMetrics.let { it.descent - it.ascent } + 2 * padV + 2 * border
    val bitmap = Bitmap.createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = 8 * density
    val outline = if (selected) WHITE else 0x99000000.toInt()
    canvas.drawRoundRect(RectF(0f, 0f, width, height), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline })
    canvas.drawRoundRect(RectF(border, border, width - border, height - border), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
    canvas.drawText(text, border + padH, border + padV - paint.fontMetrics.ascent, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun LatLon.toGeoPoint() = GeoPoint(lat, lon)

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Hands [place] to a maps or navigation app (geo: URI), else to OpenStreetMap's directions in the browser. */
private fun openDirections(context: Context, place: LatLon, name: String) {
    val coordinates = "%.6f,%.6f".format(java.util.Locale.ROOT, place.lat, place.lon)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$coordinates(${Uri.encode(name)})"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/directions?to=$coordinates"))
    try {
        context.startActivity(geo)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_map_app, Toast.LENGTH_SHORT).show()
        }
    }
}
