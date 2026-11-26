package io.github.dnicotra.ovinfode.data.api

import io.github.dnicotra.ovinfode.data.model.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The server answered, but not with a success status. */
class ApiException(val code: Int, message: String?) : Exception(message ?: "HTTP $code")

/**
 * Minimal client for the Transitous MOTIS 2 API.
 *
 * Usage policy (https://transitous.org/api/): open-source, non-commercial apps only, every
 * request carries a User-Agent with app name, version and a contact URL, and the data
 * sources must be credited in the app.
 */
class TransitousApi(
    userAgent: String,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    client: OkHttpClient = OkHttpClient(),
) {
    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            // First in the chain, so every other interceptor already sees the final headers.
            interceptors().add(0) { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .build(),
                )
            }
        }
        .build()

    /** Free-text stop search. */
    suspend fun geocode(text: String, near: LatLon?, language: String = defaultLanguage()): List<MatchDto> =
        get("api/v1/geocode") {
            addQueryParameter("text", text)
            addQueryParameter("type", "STOP")
            addQueryParameter("numResults", "15")
            addQueryParameter("language", language)
            near?.let { addQueryParameter("place", it.toParam()) }
        }

    /** All (grouped) stops inside a bounding box. */
    suspend fun mapStops(min: LatLon, max: LatLon, language: String = defaultLanguage()): List<PlaceDto> =
        get("api/v6/map/stops") {
            addQueryParameter("min", min.toParam())
            addQueryParameter("max", max.toParam())
            addQueryParameter("grouped", "true")
            addQueryParameter("language", language)
        }

    /**
     * Next departures of a stop. [center] is a fallback for when [stopId] no longer exists
     * (ids can change when the underlying timetable feeds are updated), which keeps saved
     * favourites working.
     */
    suspend fun stopTimes(
        stopId: String,
        center: LatLon?,
        count: Int,
        pageCursor: String? = null,
        language: String = defaultLanguage(),
    ): StopTimesResponse = get("api/v6/stoptimes") {
        addQueryParameter("stopId", stopId)
        if (center != null) {
            addQueryParameter("center", center.toParam())
            addQueryParameter("radius", FALLBACK_RADIUS_METERS.toString())
        }
        addQueryParameter("n", count.toString())
        addQueryParameter("language", language)
        pageCursor?.let { addQueryParameter("pageCursor", it) }
    }

    /** A single trip with all its stops. */
    suspend fun trip(tripId: String, language: String = defaultLanguage()): ItineraryDto =
        get("api/v6/trip") {
            addQueryParameter("tripId", tripId)
            addQueryParameter("detailedLegs", "false")
            addQueryParameter("language", language)
        }

    private suspend inline fun <reified T> get(path: String, params: HttpUrl.Builder.() -> Unit): T {
        val url = baseUrl.newBuilder().addPathSegments(path).apply(params).build()
        val body = http.newCall(Request.Builder().url(url).get().build()).await()
        return json.decodeFromString<T>(body)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.transitous.org/"
        private const val FALLBACK_RADIUS_METERS = 30

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }

        fun defaultLanguage(): String = Locale.getDefault().language.ifBlank { "de" }
    }
}

private fun LatLon.toParam() = "%.6f,%.6f".format(Locale.ROOT, lat, lon)

private suspend fun Call.await(): String = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    val text = it.body.string()
                    if (it.isSuccessful) {
                        cont.resume(text)
                    } else {
                        val message = runCatching { TransitousApi.json.decodeFromString<ErrorDto>(text).error }.getOrNull()
                        cont.resumeWithException(ApiException(it.code, message))
                    }
                }
            } catch (e: IOException) {
                cont.resumeWithException(e)
            }
        }
    })
}
