package io.github.dnicotra.ovinfode.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.util.placeKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the town a stop lies in (via reverse geocoding) and remembers it on the device, so
 * every place costs at most one request, ever.
 */
class TownResolver(
    private val api: TransitousApi,
    private val store: DataStore<Preferences>? = null,
) {
    /** placeKey → town; "" means "looked up, no town known". */
    private val cache = ConcurrentHashMap<String, String>()
    private val loadLock = Mutex()
    @Volatile private var loaded = store == null
    private val parallel = Semaphore(MAX_PARALLEL)

    /**
     * Towns of [places], keyed by [placeKey]. Unknown places are looked up for at most
     * [budgetMillis]; anything slower is simply looked up again on the next call.
     */
    suspend fun towns(places: Collection<NamedPlace>, budgetMillis: Long = DEFAULT_BUDGET_MS): Map<String, String> {
        ensureLoaded()
        val missing = places.distinctBy(::placeKey).filterNot { cache.containsKey(placeKey(it)) }.take(MAX_LOOKUPS_PER_CALL)
        if (missing.isNotEmpty()) {
            withTimeoutOrNull(budgetMillis) {
                coroutineScope {
                    missing.map { place -> async { parallel.withPermit { lookUp(place) } } }.awaitAll()
                }
            }
        }
        return places.mapNotNull { place ->
            val key = placeKey(place)
            cache[key]?.takeIf { it.isNotEmpty() }?.let { key to it }
        }.toMap()
    }

    private suspend fun lookUp(place: NamedPlace) {
        val matches = safeCall { api.reverseGeocode(LatLon(place.lat, place.lon)) }.getOrNull()
            ?: return // network problem: try again next time
        val town = matches.firstOrNull()?.areas?.firstOrNull { it.default == true }?.name.orEmpty()
        val key = placeKey(place)
        cache[key] = town
        store?.let { s -> safeCall { s.edit { it[stringPreferencesKey(key)] = town } } }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            safeCall { store?.data?.first() }.getOrNull()?.asMap()?.forEach { (key, value) ->
                if (value is String) cache[key.name] = value
            }
            loaded = true
        }
    }

    private companion object {
        const val MAX_PARALLEL = 4
        const val MAX_LOOKUPS_PER_CALL = 24
        const val DEFAULT_BUDGET_MS = 2500L
    }
}
