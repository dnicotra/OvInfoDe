package io.github.dnicotra.ovinfode.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import io.github.dnicotra.ovinfode.data.model.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * One-shot location lookup using the platform LocationManager, so the app also works on
 * phones without Google Play services.
 */
class LocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean = hasFine() || hasCoarse()

    /** True when only the approximate ("coarse") location was granted. */
    fun isApproximateOnly(): Boolean = hasCoarse() && !hasFine()

    private fun hasFine() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasCoarse() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Cached location without waiting for a fix (e.g. to bias search results). */
    fun lastKnown(): LatLon? = if (hasPermission()) lastKnownLocation()?.toLatLon() else null

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLon? {
        if (!hasPermission() || manager == null) return null
        val lastKnown = lastKnownLocation()
        if (lastKnown != null && ageMillis(lastKnown) < FRESH_ENOUGH_MS) return lastKnown.toLatLon()

        val provider = bestProvider() ?: return lastKnown?.toLatLon()
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
                    cont.resume(location)
                }
            }
        }
        return (fresh ?: lastKnown)?.toLatLon()
    }

    private fun bestProvider(): String? {
        val enabled = manager?.getProviders(true).orEmpty()
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return preferred.firstOrNull { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? = manager?.getProviders(true).orEmpty()
        .mapNotNull { runCatching { manager?.getLastKnownLocation(it) }.getOrNull() }
        .minByOrNull { ageMillis(it) }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun Location.toLatLon() = LatLon(latitude, longitude)

    private companion object {
        const val FRESH_ENOUGH_MS = 2 * 60 * 1000L
        const val TIMEOUT_MS = 12_000L
    }
}
