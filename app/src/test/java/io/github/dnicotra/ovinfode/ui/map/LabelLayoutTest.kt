package io.github.dnicotra.ovinfode.ui.map

import io.github.dnicotra.ovinfode.Fixtures
import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.util.platformPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** Labels on the stop map must not cover each other, other labels' places or the stop's pin. */
class LabelLayoutTest {
    private val spacing = LabelSpacing(gap = 6f, dot = 12f, step = 18f, maxDistance = 192f)

    private fun label(x: Float, y: Float, width: Float = 120f, height: Float = 80f, required: Boolean = false) =
        LabelRequest(x, y, width, height, required)

    private fun assertApart(labels: List<LabelRequest>, boxes: List<ScreenBox?>, obstacles: List<ScreenBox> = emptyList()) {
        boxes.forEachIndexed { i, box ->
            if (box == null) return@forEachIndexed
            assertEquals(labels[i].width, box.right - box.left, 0.01f)
            obstacles.forEach { assertFalse("label $i covers an obstacle", it.overlaps(box)) }
            boxes.forEachIndexed { j, other ->
                if (j != i && other != null) assertFalse("labels $i and $j overlap", box.overlaps(other, spacing.gap - 0.01f))
                if (j != i) assertFalse("label $i covers the place of label $j", box.contains(labels[j].x, labels[j].y))
            }
        }
    }

    @Test
    fun `a label with room sits on its place`() {
        val boxes = placeLabels(listOf(label(500f, 500f)), emptyList(), spacing)
        assertEquals(ScreenBox.around(500f, 500f, 120f, 80f), boxes.single())
    }

    @Test
    fun `labels on almost the same place both show, side by side`() {
        // Gleis 3 and Gleis 4 of the S-Bahn at Alexanderplatz: a few metres apart.
        val labels = listOf(label(500f, 500f, required = true), label(510f, 520f))
        val boxes = placeLabels(labels, emptyList(), spacing)
        boxes.forEach { assertNotNull(it) }
        assertApart(labels, boxes)
        // Each moved off the other's place only as far as needed.
        boxes.forEachIndexed { i, box -> assertTrue(distanceToBox(labels[i], box!!) <= spacing.dot + spacing.gap + spacing.step) }
    }

    @Test
    fun `labels keep clear of the stop's pin`() {
        val pin = ScreenBox(480f, 440f, 520f, 500f)
        val labels = listOf(label(500f, 490f))
        val boxes = placeLabels(labels, listOf(pin), spacing)
        assertApart(labels, boxes, listOf(pin))
        assertFalse(boxes.single()!!.contains(500f, 490f))
    }

    @Test
    fun `labels that find no room near their place are left out, the selected one never`() {
        val labels = (0 until 40).map { label(500f, 500f, required = it == 0) }
        val boxes = placeLabels(labels, emptyList(), spacing)
        assertNotNull(boxes.first())
        assertTrue(boxes.count { it == null } > 0)
        assertTrue(boxes.count { it != null } > 4)
        assertApart(labels, boxes)
        boxes.forEachIndexed { i, box -> if (box != null) assertTrue(distanceToBox(labels[i], box) <= spacing.maxDistance) }
    }

    @Test
    fun `the selected label shows even with no room at all`() {
        val wall = ScreenBox(0f, 0f, 1000f, 1000f)
        val boxes = placeLabels(listOf(label(500f, 500f, required = true), label(520f, 500f)), listOf(wall), spacing)
        assertEquals(ScreenBox.around(500f, 500f, 120f, 80f), boxes[0])
        assertNull(boxes[1])
    }

    @Test
    fun `labels prefer to move up or down before sideways`() {
        val labels = listOf(label(500f, 500f), label(500f, 500f))
        val boxes = placeLabels(labels, emptyList(), spacing)
        boxes.forEach { box -> assertEquals(500f, (box!!.left + box.right) / 2, 0.5f) }
    }

    /**
     * Berlin Alexanderplatz, recorded departures, at the map's opening zoom on a phone (xxhdpi): the
     * S-Bahn's Gleis 3 and 4 lie a few metres apart next to the stop's pin, the U-Bahn and tram
     * platforms around them. Every label shows, and none covers another.
     */
    @Test
    fun `alexanderplatz at the opening zoom`() {
        val density = 3f
        val stop = LatLon(52.52151, 13.411267)
        val places = platformPlaces(Fixtures.berlin.departures)
        val labels = places.mapIndexed { i, place ->
            val (x, y) = pixels(place.location, 18.0, density)
            val text = place.platforms.firstOrNull()?.let { "Gl. $it" } ?: place.lines.take(3).joinToString(" ") { it.name }
            // Bold 13 sp: about 8 dp a character, 8 dp padding and 1 dp outline on either side.
            LabelRequest(x, y, (text.length * 8 + 18) * density, 27 * density, required = i == 0)
        }
        val (px, py) = pixels(stop, 18.0, density)
        val pin = ScreenBox(px - 13 * density, py - 40 * density, px + 13 * density, py)
        val boxes = placeLabels(labels, listOf(pin), LabelSpacing(2 * density, 4 * density, 6 * density, 64 * density))
        assertApart(labels, boxes, listOf(pin))
        assertTrue("every label shows: $boxes", boxes.all { it != null })
    }

    private fun distanceToBox(label: LabelRequest, box: ScreenBox): Float {
        val dx = max(0f, max(box.left - label.x, label.x - box.right))
        val dy = max(0f, max(box.top - label.y, label.y - box.bottom))
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Screen pixels at [zoom] (Web Mercator, osmdroid's tiles scaled to the screen's density), with
     * Alexanderplatz at (1000, 1000) so that floats keep their precision.
     */
    private fun pixels(location: LatLon, zoom: Double, density: Float): Pair<Float, Float> {
        val size = 256 * density * 2.0.pow(zoom)
        fun x(lon: Double) = (lon + 180) / 360 * size
        fun y(latDegrees: Double) = (latDegrees * PI / 180).let { lat -> (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * size }
        return (x(location.lon) - x(13.411267) + 1000).toFloat() to (y(location.lat) - y(52.52151) + 1000).toFloat()
    }
}
