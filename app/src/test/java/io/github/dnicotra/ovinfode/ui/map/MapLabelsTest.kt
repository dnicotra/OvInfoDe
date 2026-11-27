package io.github.dnicotra.ovinfode.ui.map

import io.github.dnicotra.ovinfode.data.model.LatLon
import io.github.dnicotra.ovinfode.data.model.Line
import io.github.dnicotra.ovinfode.data.model.NearbyStop
import io.github.dnicotra.ovinfode.data.model.Stop
import io.github.dnicotra.ovinfode.data.model.TransportMode
import io.github.dnicotra.ovinfode.util.PlatformPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Marker labels must be readable on the (always light) map, whatever the app's theme. */
class MapLabelsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun platform(vararg lines: Line) = MapTarget.Platform(PlatformPlace(LatLon(49.41, 8.69), "Bismarckplatz", listOf("A"), lines.toList()))

    @Test
    fun `text reads on the background`() {
        assertEquals(white, readableTextOn(0xFF1F3A5F.toInt()))
        assertEquals(black, readableTextOn(0xFFF5C542.toInt())) // RNV 22 yellow
        assertEquals(black, readableTextOn(white))
        assertEquals(white, readableTextOn(black))
    }

    @Test
    fun `platforms in the colour of their line, else dark`() {
        val rnv22 = Line("RNV 22", TransportMode.TRAM, color = 0xFFF5C542.toInt())
        assertEquals(0xFFF5C542.toInt() to black, labelColors(platform(rnv22)))
        val withText = Line("RNV 5", TransportMode.TRAM, color = 0xFF3C8D4F.toInt(), textColor = white)
        assertEquals(0xFF3C8D4F.toInt() to white, labelColors(platform(withText)))
        val (background, text) = labelColors(platform(Line("754", TransportMode.BUS)))
        assertNotEquals(white, background)
        assertEquals(white, text)
        val other = MapTarget.OtherStop(NearbyStop(Stop("x", "Bismarckplatz Nord", 49.41, 8.69), 80))
        assertEquals(white, labelColors(other).second)
        assertNotEquals(white, labelColors(other).first)
    }
}
