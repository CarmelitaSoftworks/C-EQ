package cl.ecualizador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.Normalizer

/**
 * Los skins originales de C-EQ: la ventana de cada uno (assets/skins/ceq-*.png) es la que arma la app
 * desde su .wsz (skins/ del repositorio, de tools/gen_original_skins.py). Con -DoriginalWindowsOut
 * las vuelve a generar en esa carpeta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalSkinsTest {
    private fun repo(path: String) = listOf(File("../$path"), File(path)).first { it.exists() }

    /** «Neón» → skins/Neon.wsz */
    private fun wszOf(skin: WinampSkin): File {
        val ascii = Normalizer.normalize(skin.name, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return repo("skins/$ascii.wsz")
    }

    private fun compose(skin: WinampSkin): IntArray =
        WinampWindow.compose(SkinArchive.read(wszOf(skin).inputStream()), SkinImport.ANDROID_DECODER)
            ?: error("${skin.name}: el .wsz no arma ventana")

    @Test
    fun windowsMatchTheirWsz() {
        val out = System.getProperty("originalWindowsOut")?.takeIf { it.isNotEmpty() }?.let { File(it).apply { mkdirs() } }
        assertEquals(5, OriginalSkins.ALL.size)
        OriginalSkins.ALL.forEach { skin ->
            val px = compose(skin)
            if (out != null) {
                val bmp = Bitmap.createBitmap(px, WinampWindow.WIDTH, WinampWindow.ATLAS_HEIGHT, Bitmap.Config.ARGB_8888)
                File(out, "${skin.archiveId}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                return@forEach
            }
            val png = repo("app/src/main/assets/skins/${skin.archiveId}.png")
            val bmp = BitmapFactory.decodeFile(png.path, BitmapFactory.Options().apply { inPremultiplied = false })
            assertEquals(WinampWindow.WIDTH, bmp.width)
            assertEquals(WinampWindow.ATLAS_HEIGHT, bmp.height)
            val saved = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
            assertArrayEquals("${skin.name}: ${png.name} no es la ventana de su .wsz (vuelve a generarla)", px, saved)
        }
    }

    /** El importador lee los mismos colores que el catálogo. */
    @Test
    fun catalogColorsMatchWsz() {
        OriginalSkins.ALL.forEach { skin ->
            val got = SkinImport.import(SkinArchive.read(wszOf(skin).inputStream()), skin.name, SkinImport.ANDROID_DECODER).single()
            assertArrayEquals("${skin.name}: analizador", skin.colors, got.colors)
        }
    }

    @Test
    fun includedInBuiltIns() {
        assertTrue(WinampSkins.BUILT_IN.containsAll(OriginalSkins.ALL))
        assertEquals("Carmelita", WinampSkins.byName("Carmelita").name)
    }
}
