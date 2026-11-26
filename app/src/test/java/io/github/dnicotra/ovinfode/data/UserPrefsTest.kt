package io.github.dnicotra.ovinfode.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs by lazy {
        UserPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("prefs.preferences_pb").also { it.delete() } })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `favourites can be added and removed`() = runBlocking {
        val stop = Stop("de-DELFI_de:05315:11201", "Köln Hbf", 50.943, 6.959, "Köln", sortedSetOf(TransportMode.LONG_DISTANCE, TransportMode.TRAM))
        assertTrue(prefs.favorites.first().isEmpty())

        prefs.toggleFavorite(stop)
        assertEquals(listOf(stop), prefs.favorites.first())
        assertTrue(prefs.isFavorite(stop.id).first())

        prefs.toggleFavorite(stop)
        assertTrue(prefs.favorites.first().isEmpty())
        assertFalse(prefs.isFavorite(stop.id).first())
    }

    @Test
    fun `view mode is remembered`() = runBlocking {
        assertEquals(DepartureViewMode.BY_TIME, prefs.viewMode.first())
        prefs.setViewMode(DepartureViewMode.BY_LINE)
        assertEquals(DepartureViewMode.BY_LINE, prefs.viewMode.first())
    }
}
