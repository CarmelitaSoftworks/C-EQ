package cl.ecualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqTest {
    @Test
    fun contentFromGenreThenApp() {
        assertEquals(ListenContent.SPEECH, AutoEq.contentOf("au.com.shiftyjelly.pocketcasts", ""))
        assertEquals(ListenContent.VIDEO, AutoEq.contentOf("com.google.android.youtube", ""))
        assertEquals(ListenContent.MUSIC, AutoEq.contentOf("com.spotify.music", ""))
        assertEquals(ListenContent.BASS_MUSIC, AutoEq.contentOf("com.spotify.music", "Reggaeton"))
        assertEquals(ListenContent.ACOUSTIC, AutoEq.contentOf("", "Classical"))
        assertEquals(ListenContent.SPEECH, AutoEq.contentOf("com.spotify.music", "Podcast"))
    }

    @Test
    fun lowVolumeAddsBassAndTreble() {
        val mid = AutoEq.curve(OutputKind.BT_HEADPHONES, ListenContent.MUSIC, 0.6f).levels
        val low = AutoEq.curve(OutputKind.BT_HEADPHONES, ListenContent.MUSIC, 0.1f).levels
        assertTrue(low[0] > mid[0])
        assertTrue(low[9] > mid[9])
        assertEquals(mid[5], low[5], 0.01f)
    }

    @Test
    fun highVolumeProtectsSmallSpeakers() {
        val mid = AutoEq.curve(OutputKind.PHONE_SPEAKER, ListenContent.MUSIC, 0.6f).levels
        val high = AutoEq.curve(OutputKind.PHONE_SPEAKER, ListenContent.MUSIC, 1f).levels
        assertTrue(high[0] < mid[0])
        // Los audífonos no pierden graves a volumen alto, solo suavizan agudos
        val hp = AutoEq.curve(OutputKind.WIRED_HEADPHONES, ListenContent.MUSIC, 1f).levels
        val hpMid = AutoEq.curve(OutputKind.WIRED_HEADPHONES, ListenContent.MUSIC, 0.6f).levels
        assertEquals(hpMid[0], hp[0], 0.01f)
        assertTrue(hp[8] < hpMid[8])
    }

    @Test
    fun speechCutsBassAndStaysInRange() {
        val music = AutoEq.curve(OutputKind.BT_PORTABLE_SPEAKER, ListenContent.MUSIC, 0.5f)
        val speech = AutoEq.curve(OutputKind.BT_PORTABLE_SPEAKER, ListenContent.SPEECH, 0.5f)
        assertTrue(speech.levels[0] < music.levels[0])
        assertTrue(speech.levels[6] > music.levels[6])
        assertEquals(0f, speech.bass, 0f)
        for (k in OutputKind.entries) for (c in ListenContent.entries) for (v in listOf(0f, 0.3f, 1f)) {
            assertTrue(AutoEq.curve(k, c, v).levels.all { it in EqCore.MIN_DB..EqCore.MAX_DB })
        }
    }
}
