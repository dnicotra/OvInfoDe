package io.github.dnicotra.ovinfode

import io.github.dnicotra.ovinfode.data.api.TransitousApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Answers API calls from recorded fixtures: [routes] maps a path to a fixture file;
 * reverse geocoding is answered from reverse_geocode.json by rounded coordinates.
 * [override] can answer any request itself (status code + body).
 */
class FakeTransitous(
    private val routes: Map<String, String> = emptyMap(),
    private val override: ((Request) -> Pair<Int, String>?)? = null,
) {
    val requests = CopyOnWriteArrayList<Request>()

    private val reverseGeocode by lazy { Json.parseToJsonElement(Fixtures.text("reverse_geocode.json")).jsonObject }

    val api = TransitousApi(
        userAgent = "Abfahrten/test (+https://example.org)",
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val (code, body) = override?.invoke(request) ?: respond(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
    )

    fun requestsTo(path: String) = requests.filter { it.url.encodedPath == path }

    private fun respond(request: Request): Pair<Int, String> {
        val path = request.url.encodedPath
        if (path == "/api/v1/reverse-geocode") {
            val (lat, lon) = request.url.queryParameter("place")!!.split(',').map { it.toDouble() }
            val key = String.format(Locale.ROOT, "%.3f,%.3f", lat, lon)
            return 200 to (reverseGeocode[key]?.toString() ?: "[]")
        }
        return routes[path]?.let { 200 to Fixtures.text(it) } ?: (404 to """{"error":"no fixture for $path"}""")
    }
}
