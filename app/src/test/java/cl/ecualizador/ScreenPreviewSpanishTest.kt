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

/** Con -DpreviewDir: la pantalla principal en español, con letra grande (ventana alta para que entre entera). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w392dp-h3600dp-xxhdpi", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenPreviewSpanishTest {
    @Test
    fun mainScreenWithLargeFont() {
        val out = File(System.getProperty("previewDir")?.takeIf { it.isNotEmpty() } ?: return)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(20) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
            scenario.onActivity { a ->
                val root = a.window.decorView
                val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bmp))
                File(out, "pantalla_letra_grande_es.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
