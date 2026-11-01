package cl.ecualizador

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import kotlin.math.sin

/**
 * Con -DpromoDir: los cuadros del video de promoción (20 por segundo) del fondo «Winamp completo» en
 * un vivo V30: suena una cola, el espectro se mueve y cada 2,5 s se pasa a la siguiente canción y a
 * otro skin, con otro preajuste del ecualizador. tools/promo.sh arma el GIF y el MP4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PromoFramesTest {
    @Test
    fun promoFrames() {
        val out = File(System.getProperty("promoDir")?.takeIf { it.isNotEmpty() } ?: return).apply { mkdirs() }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        WinampWindows.init(ctx)
        WinampSkins.load(ctx)
        val engine = EqEngine.get(ctx)
        val skins = listOf(WinampSkins.DEFAULT.name, "Major Tom", "Expensive Hi-Fi", "Bento Classified")
        val presets = listOf("Rock", "Fiesta", "Graves y agudos", "Pop")
        val songs = listOf(
            "Llama Whippin' Intro" to "DJ Mike Llama", "Stumble" to "Just Plain Ant", "Ocean Eyes" to "Neon Tide",
            "Sábado en la noche" to "Los Ejemplos", "Midnight Drive" to "Synth Club", "Cassette Summer" to "The Rewinders",
            "Bajo cero" to "Frío Polar", "Último tema" to "Banda", "Dial-Up Love" to "Modem Hearts", "Llama Song (remix)" to "DJ Mike Llama",
        )
        NowPlaying.queue.value = songs.mapIndexed { i, (t, a) -> QueueEntry(i.toLong(), t, a) }
        val durations = listOf(225_000L, 198_000L, 241_000L, 187_000L, 263_000L)

        val w = 1260; val h = 2800; val density = 3f
        val margin = 16 * density
        val view = WallpaperView.FULL_SKIN
        val renderer = SpectrumRenderer()
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1A1033.toInt(), 0xFF0F1115.toInt(), Shader.TileMode.CLAMP) }
        val bars = SpectrumSettings.WINAMP_BARS_ORIGINAL
        val rnd = java.util.Random(7)
        val level = FloatArray(bars)
        val peaks = FloatArray(bars)
        val fps = 20
        val perSkin = fps * 5 / 2
        var frame = 0
        for ((k, skin) in skins.withIndex()) {
            // Siguiente canción: el botón se ve presionado y la cola avanza
            NowPlaying.activeQueueId = k.toLong()
            val (title, artist) = songs[k]
            NowPlaying.track.value = PlayingTrack(
                title, artist, durations[k % durations.size], 12_000L + k * 31_000L,
                android.os.SystemClock.elapsedRealtime(), 1f, PlayingTrack.State.PLAYING, app = "com.spotify.music",
            )
            runCatching { engine.usePreset(presets[k]) }
            if (k > 0) renderer.press(WinampWindow.Button.NEXT)
            val base = SpectrumSettings(style = SpectrumStyle.WINAMP_CLASSIC, winampSkin = skin)
            val sh = view.height(base, w - 2 * margin, h.toFloat(), 0.25f)
            val top = h - sh - h * 0.14f
            for (f in 0 until perSkin) {
                // Música inventada: golpe de bombo cada medio segundo y bandas que respiran
                val t = frame / fps.toFloat()
                val beat = Math.exp(-((t % 0.5f) * 9.0)).toFloat()
                for (b in 0 until bars) {
                    val shape = 0.85f - b * 0.03f
                    val target = (shape * (0.55f + 0.45f * beat * (1f - b / bars.toFloat())) +
                        0.18f * sin(t * 3.1f + b * 0.9f) + 0.12f * (rnd.nextFloat() - 0.5f)).coerceIn(0.03f, 1f)
                    level[b] = if (target > level[b]) target else level[b] - (level[b] - target) * 0.35f
                    peaks[b] = if (level[b] >= peaks[b]) level[b] else (peaks[b] - 0.02f).coerceAtLeast(level[b])
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
                c.save(); c.translate(margin, top)
                renderer.draw(c, w - 2 * margin, sh, level, peaks, view.spectrumSettings(base), density)
                c.restore()
                File(out, "f%04d.png".format(frame)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                frame++
                ShadowSystemClock.advanceBy(Duration.ofMillis(1000L / fps))
            }
        }
        NowPlaying.track.value = null
        NowPlaying.queue.value = emptyList()
    }
}
