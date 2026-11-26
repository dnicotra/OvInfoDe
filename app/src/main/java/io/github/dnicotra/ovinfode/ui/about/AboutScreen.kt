@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.dnicotra.ovinfode.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.dnicotra.ovinfode.BuildConfig
import io.github.dnicotra.ovinfode.R
import io.github.dnicotra.ovinfode.ui.theme.LocalStatusColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)

            Section(stringResource(R.string.about_legend_title))
            Text(stringResource(R.string.about_legend_ontime), color = LocalStatusColors.current.onTime)
            Text(stringResource(R.string.about_legend_delayed), color = LocalStatusColors.current.delayed)
            Text(stringResource(R.string.about_legend_schedule))

            Section(stringResource(R.string.about_data_title))
            Text(stringResource(R.string.about_data_text))
            Link(stringResource(R.string.about_link_sources), "https://transitous.org/sources/")
            Link(stringResource(R.string.about_link_osm), "https://www.openstreetmap.org/copyright")
            Link(stringResource(R.string.about_link_donate), "https://transitous.org/donate/")

            Section(stringResource(R.string.about_privacy_title))
            Text(stringResource(R.string.about_privacy_text))

            Section(stringResource(R.string.about_source_title))
            Link(stringResource(R.string.about_link_source), BuildConfig.PROJECT_URL)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Link(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable { runCatching { uriHandler.openUri(url) } }
            .padding(vertical = 4.dp),
    )
}
