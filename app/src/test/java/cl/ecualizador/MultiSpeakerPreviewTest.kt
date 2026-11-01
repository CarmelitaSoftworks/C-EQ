package cl.ecualizador

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Con -DpreviewDir: la pantalla con el asistente «Varios parlantes» de ejemplo (dos JBL y un Sony, en
 * un teléfono que transmite LE Audio), en inglés y en español, para la portada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MultiSpeakerPreviewTest {
    private fun capture(name: String) {
        val out = File(System.getProperty("previewDir")?.takeIf { it.isNotEmpty() } ?: return)
        val speakers = listOf("JBL Charge 5", "JBL Flip 6", "SRS-XB43")
        MultiSpeaker.demo = speakers to MultiSpeaker.plan(speakers, broadcast = true, samsung = false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                repeat(20) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
                scenario.onActivity { a ->
                    val root = a.window.decorView
                    val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bmp))
                    File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        } finally {
            MultiSpeaker.demo = null
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w392dp-h3600dp-xxhdpi")
    fun english() = capture("varios_parlantes_en.png")

    @Test
    @Config(sdk = [34], qualifiers = "es-w392dp-h3600dp-xxhdpi")
    fun spanish() = capture("varios_parlantes_es.png")
}
