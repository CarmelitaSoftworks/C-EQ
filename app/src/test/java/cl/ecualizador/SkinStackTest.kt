package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinStackTest {
    @Test
    fun atlasPiecesDoNotOverlapAndFit() {
        assertEquals(WinampWindow.LIVE_HEIGHT, WinampWindow.EQ_Y)
        assertTrue(WinampWindow.EQ_SLIDERS_Y + 129 <= WinampWindow.PL_Y)
        assertTrue(WinampWindow.EQ_THUMB_X + 28 + 26 <= WinampWindow.WIDTH)
        assertTrue(WinampWindow.PL_HANDLE_X + 8 <= WinampWindow.WIDTH)
        assertEquals(WinampWindow.PL_BOTTOM_RIGHT_Y + WinampWindow.PL_BOTTOM_H, WinampWindow.ATLAS_HEIGHT)
    }

    @Test
    fun equalizerWindowHits() {
        assertEquals(WinampWindow.Hit.EqToggle, WinampWindow.eqHit(20f, 22f))
        assertEquals(WinampWindow.Hit.EqAuto, WinampWindow.eqHit(50f, 22f))
        assertEquals(WinampWindow.Hit.OpenEqualizer, WinampWindow.eqHit(230f, 22f))
        // Arriba del todo de la primera banda: +12 dB; abajo del todo de la última: −12 dB
        assertEquals(WinampWindow.Hit.EqBand(0, 1f), WinampWindow.eqHit(84f, 38f))
        assertEquals(WinampWindow.Hit.EqBand(9, 0f), WinampWindow.eqHit(78f + 9 * 18 + 6, 100f))
        assertEquals(-1, (WinampWindow.eqHit(27f, 70f) as WinampWindow.Hit.EqBand).band)
        assertNull(WinampWindow.eqHit(150f, 5f))
    }

    @Test
    fun sliderFramesGoFromBottomToTop() {
        assertEquals(0 to WinampWindow.EQ_SLIDERS_Y, WinampWindow.eqSliderFrame(0f))
        assertEquals(13 * 15 to WinampWindow.EQ_SLIDERS_Y + 65, WinampWindow.eqSliderFrame(1f))
    }

    @Test
    fun stackUsesIntegerScaleAndFillsWithPlaylistRows() {
        // Vivo V30: 1260 px de ancho menos márgenes, hasta el 72 % de 2800 px de alto
        val w = 1260f - 96f
        val h = SkinStack.height(w, 2800f * 0.72f)
        val scale = SkinStack.scale(w, h)
        assertEquals(4f, scale)
        val sides = SkinStack.sides(h, scale)
        assertTrue(sides >= 2)
        assertEquals((SkinStack.FIXED + WinampWindow.playlistHeight(sides)) * scale, h, 0.01f)
        assertTrue(WinampWindow.playlistRows(sides) >= 4)
    }
}
