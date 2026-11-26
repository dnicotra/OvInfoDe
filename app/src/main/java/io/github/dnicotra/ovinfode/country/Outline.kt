package io.github.dnicotra.ovinfode.country

import io.github.dnicotra.ovinfode.data.model.LatLon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A country's land area as simplified polygons (see tools/country_outlines.py), to tell which
 * country a stop lies in. The Natural Earth borders are generalised: within a kilometre or two
 * of a border (Vaals, Bad Nieuweschans) a stop can land in the neighbouring country. That is
 * acceptable because ids with a country code, such as all German DHIDs, are checked first
 * (see [Countries.of]), and a wrong guess only changes naming conventions.
 */
internal class Outline(encodedPolygons: List<String>) {
    private val polygons: List<Polygon> by lazy { encodedPolygons.map { Polygon(decodePolyline(it)) } }

    operator fun contains(point: LatLon): Boolean = polygons.any { it.contains(point) }

    /** True when [point] is inside or at most [km] outside the outline (piers, ferry terminals). */
    fun isNear(point: LatLon, km: Double): Boolean = polygons.any { it.contains(point) || it.distanceKm(point, km) <= km }
}

private class Polygon(points: List<LatLon>) {
    private val lats = DoubleArray(points.size) { points[it].lat }
    private val lons = DoubleArray(points.size) { points[it].lon }
    private val minLat = lats.min()
    private val maxLat = lats.max()
    private val minLon = lons.min()
    private val maxLon = lons.max()

    /** Even-odd ray casting. */
    fun contains(point: LatLon): Boolean {
        val (lat, lon) = point
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        var inside = false
        var j = lats.lastIndex
        for (i in lats.indices) {
            if ((lats[i] > lat) != (lats[j] > lat) &&
                lon < (lons[j] - lons[i]) * (lat - lats[i]) / (lats[j] - lats[i]) + lons[i]
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Distance to the nearest edge in km; [Double.MAX_VALUE] when clearly further than [limitKm]. */
    fun distanceKm(point: LatLon, limitKm: Double): Double {
        val margin = limitKm / KM_PER_DEGREE * 2
        if (point.lat < minLat - margin || point.lat > maxLat + margin) return Double.MAX_VALUE
        val k = cos(Math.toRadians(point.lat))
        if (point.lon < minLon - margin / k || point.lon > maxLon + margin / k) return Double.MAX_VALUE
        // Equirectangular projection around the point: exact enough over a few kilometres.
        fun x(i: Int) = (lons[i] - point.lon) * k
        fun y(i: Int) = lats[i] - point.lat
        var best = Double.MAX_VALUE
        var j = lats.lastIndex
        for (i in lats.indices) {
            val ax = x(j)
            val ay = y(j)
            val dx = x(i) - ax
            val dy = y(i) - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / lengthSquared))
            val px = ax + t * dx
            val py = ay + t * dy
            best = min(best, px * px + py * py)
            j = i
        }
        return sqrt(best) * KM_PER_DEGREE
    }

    private companion object {
        const val KM_PER_DEGREE = 111.195
    }
}

/**
 * Decodes a Google encoded polyline (precision 1e-5). Characters outside the polyline
 * alphabet, such as the line breaks and indentation of the generated source, are skipped.
 */
internal fun decodePolyline(encoded: String): List<LatLon> {
    val chars = encoded.filter { it.code in 63..126 }
    val points = ArrayList<LatLon>(chars.length / 4)
    var index = 0
    fun nextValue(): Int {
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = chars[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }
    var lat = 0
    var lon = 0
    while (index < chars.length) {
        lat += nextValue()
        lon += nextValue()
        points += LatLon(lat / 1e5, lon / 1e5)
    }
    return points
}
