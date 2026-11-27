package io.github.dnicotra.ovinfode.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.dnicotra.ovinfode.country.Countries
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class DepartureViewMode { BY_TIME, BY_LINE }

/** Favourite stops and small UI preferences, stored on the device only. */
class UserPrefs(private val store: DataStore<Preferences>) {

    @Serializable
    private data class StoredStop(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val area: String? = null,
        val modes: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    val favorites: Flow<List<Stop>> = store.data
        .map { prefs -> decode(prefs[FAVORITES]) }
        .distinctUntilChanged()

    fun isFavorite(stopId: String): Flow<Boolean> = favorites.map { list -> list.any { it.id == stopId } }.distinctUntilChanged()

    suspend fun toggleFavorite(stop: Stop) {
        store.edit { prefs ->
            val current = decode(prefs[FAVORITES])
            val updated = if (current.any { it.id == stop.id }) current.filterNot { it.id == stop.id } else current + stop
            prefs[FAVORITES] = encode(updated)
        }
    }

    val viewMode: Flow<DepartureViewMode> = store.data
        .map { prefs -> prefs[VIEW_MODE]?.let { runCatching { DepartureViewMode.valueOf(it) }.getOrNull() } ?: DepartureViewMode.BY_TIME }
        .distinctUntilChanged()

    suspend fun setViewMode(mode: DepartureViewMode) {
        store.edit { it[VIEW_MODE] = mode.name }
    }

    private fun decode(raw: String?): List<Stop> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredStop>>(raw) }.getOrDefault(emptyList()).map {
            Stop(
                id = it.id,
                // Favourites saved by older versions get the current names too.
                name = Countries.of(it.id, it.lat, it.lon).stopName(it.name, Countries.ofFeed(it.id), it.id),
                lat = it.lat,
                lon = it.lon,
                area = it.area,
                modes = it.modes.mapNotNull { m -> runCatching { TransportMode.valueOf(m) }.getOrNull() }.toSortedSet(),
            )
        }
    }

    private fun encode(stops: List<Stop>): String = json.encodeToString(
        stops.map { StoredStop(it.id, it.name, it.lat, it.lon, it.area, it.modes.map(TransportMode::name)) },
    )

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val VIEW_MODE = stringPreferencesKey("departure_view_mode")
    }
}
