package io.github.dnicotra.ovinfode

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.dnicotra.ovinfode.data.LocationProvider
import io.github.dnicotra.ovinfode.data.TownResolver
import io.github.dnicotra.ovinfode.data.TransitRepository
import io.github.dnicotra.ovinfode.data.UserPrefs
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import org.osmdroid.config.Configuration
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "user_prefs")
private val Context.townStore by preferencesDataStore(name = "place_towns")

class OvInfoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Map tiles: OpenStreetMap's tile servers want to know the app; tiles are cached with the app's other caches.
        Configuration.getInstance().apply {
            userAgentValue = container.userAgent
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}

/** Manual dependency injection: one instance of each service per process. */
class AppContainer(context: Context) {
    val userAgent = "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.PROJECT_URL})"
    val api = TransitousApi(userAgent = userAgent)
    val repository = TransitRepository(api, TownResolver(api, context.townStore))
    val prefs = UserPrefs(context.dataStore)
    val location = LocationProvider(context)
}
