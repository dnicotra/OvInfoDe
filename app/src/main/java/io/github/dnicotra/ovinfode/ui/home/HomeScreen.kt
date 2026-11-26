@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dnicotra.ovinfode.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.country.country
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.ui.components.AttributionFooter
import io.github.dnicotra.ovinfode.ui.components.CenteredMessage
import io.github.dnicotra.ovinfode.ui.components.CompactDepartureRow
import io.github.dnicotra.ovinfode.ui.components.ErrorMessage
import io.github.dnicotra.ovinfode.ui.components.LoadingIndicator
import io.github.dnicotra.ovinfode.ui.components.ModeIcons
import io.github.dnicotra.ovinfode.ui.components.messageRes
import io.github.dnicotra.ovinfode.ui.components.rememberNow
import io.github.dnicotra.ovinfode.util.upcoming
import java.time.Instant
import java.util.Locale

private val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenStop: (Stop) -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val context = LocalContext.current
    var askedAutomatically by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        if (!granted) {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        viewModel.onPermissionResult(granted)
    }

    // Ask once, the first time the nearby tab is shown without permission.
    LaunchedEffect(state.tab, state.nearby) {
        if (state.tab == HomeTab.NEARBY && state.nearby == NearbyState.NoPermission && !askedAutomatically) {
            askedAutomatically = true
            permissionLauncher.launch(locationPermissions)
        }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }

    HomeContent(
        state = state,
        now = now,
        onSelectTab = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onRequestPermission = {
            if (permanentlyDenied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            } else {
                permissionLauncher.launch(locationPermissions)
            }
        },
        onOpenStop = onOpenStop,
        onToggleFavorite = viewModel::toggleFavorite,
        onSearch = onSearch,
        onAbout = onAbout,
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Stateless home screen, also used by the screenshot tests. */
@Composable
fun HomeContent(
    state: HomeUiState,
    now: Instant,
    onSelectTab: (HomeTab) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenStop: (Stop) -> Unit,
    onToggleFavorite: (Stop) -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchPill(onSearch, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            val tabs = listOf(HomeTab.NEARBY to R.string.tab_nearby, HomeTab.FAVORITES to R.string.tab_favorites)
            PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == state.tab }.coerceAtLeast(0)) {
                tabs.forEach { (tab, label) ->
                    Tab(selected = state.tab == tab, onClick = { onSelectTab(tab) }, text = { Text(stringResource(label)) })
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when (state.tab) {
                    HomeTab.NEARBY -> NearbyTab(state, now, onRequestPermission, onRefresh, onOpenStop, onToggleFavorite)
                    HomeTab.FAVORITES -> FavoritesTab(state, now, onOpenStop, onToggleFavorite)
                    null -> LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NearbyTab(
    state: HomeUiState,
    now: Instant,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onOpenStop: (Stop) -> Unit,
    onToggleFavorite: (Stop) -> Unit,
) {
    // Every branch is scrollable so pull-to-refresh works everywhere.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val nearby = state.nearby) {
            NearbyState.NoPermission -> item {
                CenteredMessage(
                    text = stringResource(R.string.location_permission_title) + "\n\n" + stringResource(R.string.location_permission_text),
                    icon = Icons.Filled.MyLocation,
                    actionLabel = stringResource(R.string.location_permission_button),
                    onAction = onRequestPermission,
                )
            }
            NearbyState.Locating -> item { LoadingIndicator(text = stringResource(R.string.locating)) }
            NearbyState.Loading -> item { LoadingIndicator() }
            NearbyState.LocationUnavailable -> item {
                CenteredMessage(
                    text = stringResource(R.string.location_unavailable),
                    icon = Icons.Filled.LocationOff,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )
            }
            is NearbyState.Error -> item { ErrorMessage(nearby.kind, onRetry) }
            is NearbyState.Loaded -> {
                if (nearby.approximateLocation) {
                    item {
                        Text(
                            stringResource(R.string.approximate_location),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (nearby.stops.isEmpty()) {
                    item { CenteredMessage(stringResource(R.string.no_nearby_stops), icon = Icons.Filled.LocationOff) }
                }
                items(nearby.stops, key = { it.stop.id }) { preview ->
                    StopCard(preview, now, preview.stop.id in state.favoriteIds, onOpenStop, onToggleFavorite)
                }
                item { AttributionFooter() }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    state: HomeUiState,
    now: Instant,
    onOpenStop: (Stop) -> Unit,
    onToggleFavorite: (Stop) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.favorites.isEmpty()) {
            item { CenteredMessage(stringResource(R.string.favorites_empty), icon = Icons.Outlined.StarBorder) }
        } else {
            items(state.favorites, key = { it.stop.id }) { preview ->
                StopCard(preview, now, isFavorite = true, onOpenStop, onToggleFavorite)
            }
            item { AttributionFooter() }
        }
    }
}

@Composable
private fun distanceText(meters: Int): String =
    if (meters < 1000) {
        stringResource(R.string.distance_m, (meters / 10) * 10)
    } else {
        stringResource(R.string.distance_km, String.format(Locale.getDefault(), "%.1f", meters / 1000.0))
    }

@Composable
private fun StopCard(
    preview: StopPreview,
    now: Instant,
    isFavorite: Boolean,
    onOpenStop: (Stop) -> Unit,
    onToggleFavorite: (Stop) -> Unit,
) {
    ElevatedCard(onClick = { onOpenStop(preview.stop) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(preview.stop.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeIcons(preview.stop.modes, remember(preview.stop.id) { preview.stop.country() })
                    val subtitle = listOfNotNull(
                        preview.distanceMeters?.let { distanceText(it) },
                        preview.stop.area?.takeIf { !preview.stop.name.contains(it) },
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        if (preview.stop.modes.isNotEmpty()) Spacer(Modifier.width(8.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = { onToggleFavorite(preview.stop) }) {
                if (isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.secondary)
                } else {
                    Icon(Icons.Outlined.StarBorder, contentDescription = stringResource(R.string.favorite_add))
                }
            }
        }
        when (val p = preview.preview) {
            null -> Spacer(Modifier.height(8.dp))
            PreviewState.Loading -> LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            is PreviewState.Failed -> Text(
                stringResource(p.kind.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            is PreviewState.Loaded -> {
                val upcoming = p.departures.upcoming(now).take(3)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (upcoming.isEmpty()) {
                        Text(stringResource(R.string.no_upcoming_departures), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    upcoming.forEach { CompactDepartureRow(it, now) }
                }
            }
        }
    }
}
