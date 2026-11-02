package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPacingTest {
    private fun drawnPerSecond(refreshHz: Int, fps: Int): Int {
        val pacer = WallpaperPacing.Pacer()
        val frameMs = 1000f / refreshHz
        return (0 until refreshHz).count { pacer.tick(frameMs, fps) }
    }

    @Test
    fun framesMatchTheChosenRateOnAnyScreen() {
        // El primer cuadro se dibuja enseguida: puede sumar uno
        assertTrue(drawnPerSecond(60, 60) in 59..61)
        assertTrue(drawnPerSecond(60, 30) in 29..31)
        // En pantallas de 90 y 120 Hz, «60» son 60 y no 90 o 120
        assertTrue(drawnPerSecond(90, 60) in 59..61)
        assertTrue(drawnPerSecond(120, 60) in 59..61)
        assertTrue(drawnPerSecond(120, 30) in 29..31)
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
