package io.github.dnicotra.ovinfode

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.dnicotra.ovinfode.data.LocationProvider
import io.github.dnicotra.ovinfode.data.TransitRepository
import io.github.dnicotra.ovinfode.data.UserPrefs
import io.github.dnicotra.ovinfode.data.api.TransitousApi

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class OvInfoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection: one instance of each service per process. */
class AppContainer(context: Context) {
    val api = TransitousApi(
        userAgent = "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (Android; +${BuildConfig.PROJECT_URL})",
    )
    val repository = TransitRepository(api)
    val prefs = UserPrefs(context.dataStore)
    val location = LocationProvider(context)
}
