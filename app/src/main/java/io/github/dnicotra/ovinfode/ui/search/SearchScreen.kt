@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dnicotra.ovinfode.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.ui.components.CenteredMessage
import io.github.dnicotra.ovinfode.ui.components.ErrorMessage
import io.github.dnicotra.ovinfode.ui.components.ModeIcons
import java.util.Locale

@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenStop: (Stop) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(state, onQueryChange = viewModel::onQueryChange, onRetry = viewModel::retry, onBack = onBack, onOpenStop = onOpenStop)
}

/** Stateless search screen, also used by the screenshot tests. */
@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenStop: (Stop) -> Unit,
    autoFocus: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = null) }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.size(4.dp))
            when {
                state.error != null -> ErrorMessage(state.error, onRetry)
                state.query.trim().length < 2 -> CenteredMessage(stringResource(R.string.search_min_chars))
                state.searched && state.results.isEmpty() -> CenteredMessage(stringResource(R.string.search_empty), icon = Icons.Filled.SearchOff)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.results, key = { it.stop.id }) { result ->
                        SearchResultItem(result) {
                            keyboard?.hide()
                            onOpenStop(result.stop)
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        supportingContent = {
            Row {
                ModeIcons(result.stop.modes)
                result.stop.area?.takeIf { !result.stop.name.contains(it) }?.let {
                    if (result.stop.modes.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = result.distanceMeters?.let { meters ->
            {
                Box {
                    Text(
                        if (meters < 1000) stringResource(R.string.distance_m, (meters / 10) * 10)
                        else stringResource(R.string.distance_km, String.format(Locale.getDefault(), "%.0f", meters / 1000.0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    ) {
        Text(result.stop.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
