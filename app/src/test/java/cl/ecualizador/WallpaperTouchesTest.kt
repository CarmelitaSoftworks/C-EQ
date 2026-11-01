package cl.ecualizador

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperTouchesTest {
    private val play = WinampWindow.Hit.Press(WinampWindow.Button.PLAY)
    private val next = WinampWindow.Hit.Press(WinampWindow.Button.NEXT)

    @Test
    fun doubleTapNeedsTheSameTarget() {
        assertTrue(WallpaperTouches.sameTarget(play, play))
        assertFalse(WallpaperTouches.sameTarget(play, next))
        assertTrue(WallpaperTouches.sameTarget(WinampWindow.Hit.Seek(0.2f), WinampWindow.Hit.Seek(0.25f)))
        assertTrue(WallpaperTouches.sameTarget(WinampWindow.Hit.EqBand(3, 0.5f), WinampWindow.Hit.EqBand(3, 0.6f)))
        assertFalse(WallpaperTouches.sameTarget(WinampWindow.Hit.EqBand(3, 0.5f), WinampWindow.Hit.EqBand(4, 0.5f)))
        assertFalse(WallpaperTouches.sameTarget(WinampWindow.Hit.PlaylistRow(1), WinampWindow.Hit.PlaylistRow(2)))
    }

    @Test
    fun doubleTapIsTheDefault() {
        assertTrue(WallpaperSettings().touch == WallpaperTouch.DOUBLE)
    }
}
