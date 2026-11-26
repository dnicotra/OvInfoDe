package io.github.dnicotra.ovinfode.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.dnicotra.ovinfode.FakeTransitous
import io.github.dnicotra.ovinfode.data.model.NamedPlace
import io.github.dnicotra.ovinfode.util.placeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TownResolverTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("towns.preferences_pb").also { it.delete() } }
    }

    @After
    fun tearDown() = scope.cancel()

    private val stuttgart = NamedPlace("de-DELFI_de:08111:6115:2:3", "Hauptbahnhof", 48.785557, 9.182444)

    @Test
    fun `towns are remembered across app restarts`() = runBlocking {
        val online = FakeTransitous()
        assertEquals(mapOf(placeKey(stuttgart) to "Stuttgart"), TownResolver(online.api, store).towns(listOf(stuttgart)))
        assertEquals(1, online.requests.size)

        // A new resolver on the same storage, with every request failing.
        val offline = FakeTransitous(override = { 500 to "{}" })
        assertEquals(mapOf(placeKey(stuttgart) to "Stuttgart"), TownResolver(offline.api, store).towns(listOf(stuttgart)))
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun `failed lookups are retried later`() = runBlocking {
        val offline = FakeTransitous(override = { 503 to "{}" })
        val resolver = TownResolver(offline.api)
        assertTrue(resolver.towns(listOf(stuttgart)).isEmpty())
        assertTrue(resolver.towns(listOf(stuttgart)).isEmpty())
        assertEquals(2, offline.requests.size)
    }

    @Test
    fun `platforms of one station share a single lookup`() = runBlocking {
        val fake = FakeTransitous()
        val otherPlatform = stuttgart.copy(id = "de-DELFI_de:08111:6115:4:7")
        TownResolver(fake.api).towns(listOf(stuttgart, otherPlatform))
        assertEquals(1, fake.requests.size)
    }
}
