package io.github.dnicotra.ovinfode.data

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.api.ApiException
import io.github.dnicotra.ovinfode.data.api.TransitousApi
import io.github.dnicotra.ovinfode.data.model.ErrorKind
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Stop
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Repository tests against canned responses (no network). */
class TransitRepositoryTest {
    private val requests = mutableListOf<Request>()

    private fun repository(respond: (Request) -> Pair<Int, String>): TransitRepository {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val (code, body) = respond(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return TransitRepository(TransitousApi(userAgent = "Abfahrten/test (+https://example.org)", client = client))
    }

    @Test
    fun `departures send stop id with coordinate fallback and a user agent`() = runBlocking {
        val repo = repository { 200 to Fixtures.text("stoptimes_berlin_alexanderplatz.json") }
        val stop = Stop("de-DELFI_de:11000:900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.52151, 13.411267)

        val result = repo.departures(stop, count = 30)

        assertEquals(Fixtures.berlin.departures.size, result.departures.size)
        val url = requests.single().url
        assertEquals("/api/v6/stoptimes", url.encodedPath)
        assertEquals("de-DELFI_de:11000:900100003", url.queryParameter("stopId"))
        assertEquals("52.521510,13.411267", url.queryParameter("center"))
        assertEquals("30", url.queryParameter("radius"))
        assertEquals("30", url.queryParameter("n"))
        assertEquals("Abfahrten/test (+https://example.org)", requests.single().header("User-Agent"))
    }

    @Test
    fun `nearby stops are sorted by distance and the radius grows until enough are found`() = runBlocking {
        val repo = repository { 200 to Fixtures.text("mapstops_koeln.json") }
        val nearby = repo.nearbyStops(LatLon(50.9420, 6.9580))

        assertEquals("Köln Dom/Hbf", nearby.first().stop.name)
        assertEquals(nearby.map { it.distanceMeters }.sorted(), nearby.map { it.distanceMeters })
        assertEquals(6, nearby.size)
        // 4 stops within 500 m is not enough, so a second request with 1000 m is made.
        assertEquals(2, requests.size)
        requests.forEach { assertEquals("/api/v6/map/stops", it.url.encodedPath) }
    }

    @Test
    fun `server errors become ApiException and map to SERVER`() = runBlocking {
        val repo = repository { 500 to """{"error":"internal"}""" }
        try {
            repo.trip("x")
            fail("expected an exception")
        } catch (e: ApiException) {
            assertEquals(500, e.code)
            assertEquals("internal", e.message)
            assertEquals(ErrorKind.SERVER, e.toErrorKind())
        }
    }

    @Test
    fun `error kinds`() {
        assertEquals(ErrorKind.NETWORK, IOException("offline").toErrorKind())
        assertEquals(ErrorKind.NOT_FOUND, ApiException(404, null).toErrorKind())
        assertTrue(safeCallResultIsFailure())
    }

    private fun safeCallResultIsFailure(): Boolean = runBlocking { safeCall<Unit> { throw IOException("x") }.isFailure }
}
