package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPacingTest {
    @Test
    fun thirtyFpsDrawsEveryOtherFrame() {
        assertTrue(WallpaperPacing.shouldDraw(60, 16))
        assertFalse(WallpaperPacing.shouldDraw(30, 16))
        assertTrue(WallpaperPacing.shouldDraw(30, 33))
        // El reloj de los cuadros tiembla un poco: 31 ms cuenta como el siguiente cuadro a 30
        assertTrue(WallpaperPacing.shouldDraw(30, 31))
    }

    @Test
    fun quietOnlyWithoutMusicAndWithAFlatSpectrum() {
        assertTrue(WallpaperPacing.isQuiet(FloatArray(19), playing = false))
        assertFalse(WallpaperPacing.isQuiet(FloatArray(19), playing = true))
        assertFalse(WallpaperPacing.isQuiet(FloatArray(19) { if (it == 3) 0.2f else 0f }, playing = false))
    }

    @Test
    fun sixtyIsTheDefault() {
        assertEquals(60, WallpaperSettings().fps)
        assertTrue(60 in WallpaperPacing.FPS_OPTIONS && 30 in WallpaperPacing.FPS_OPTIONS)
    }
}
