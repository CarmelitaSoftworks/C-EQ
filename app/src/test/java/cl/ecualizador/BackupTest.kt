package cl.ecualizador

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun roundTripRestoresPreferencesAndFiles() {
        ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putFloat("surround", 0.4f).putString("headroom", "FULL").putInt("n", 7).putLong("l", 9L)
            .putStringSet("set", setOf("a", "b")).commit()
        val dir = File(ctx.filesDir, "skin-windows").apply { mkdirs() }
        File(dir, "123.png").writeBytes(byteArrayOf(1, 2, 3))
        File(ctx.filesDir, "winamp-skins.json").writeText("""[{"name":"Mío","window":"/otro/teléfono/files/skin-windows/123.png","colors":[],"chrome":[]}]""")
        File(ctx.filesDir, "wallpaper-original").writeBytes(byteArrayOf(9, 9))

        val zip = ByteArrayOutputStream().also { Backup.export(ctx, it) }.toByteArray()

        // Se borra todo y se restaura
        ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", false).commit()
        dir.listFiles()?.forEach { it.delete() }
        File(ctx.filesDir, "wallpaper-original").delete()
        Backup.import(ctx, ByteArrayInputStream(zip))

        val p = ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE)
        assertTrue(p.getBoolean("enabled", false))
        assertEquals(0.4f, p.getFloat("surround", 0f), 0.0001f)
        assertEquals("FULL", p.getString("headroom", null))
        assertEquals(7, p.getInt("n", 0))
        assertEquals(9L, p.getLong("l", 0))
        assertEquals(setOf("a", "b"), p.getStringSet("set", null))
        assertTrue(File(dir, "123.png").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(ctx.filesDir, "wallpaper-original").exists())
        // La ruta de la ventana apunta a la carpeta de este teléfono
        assertTrue(File(ctx.filesDir, "winamp-skins.json").readText().contains(File(dir, "123.png").path.replace("/", "\\/")))
    }

    @Test
    fun rejectsFilesThatAreNotBackupsWithoutChangingAnything() {
        ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        val notBackup = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("prefs/eq2.json")); z.write("{}".toByteArray()); z.closeEntry() }
        }.toByteArray()
        assertFalse(runCatching { Backup.import(ctx, ByteArrayInputStream(notBackup)) }.isSuccess)
        assertFalse(runCatching { Backup.import(ctx, ByteArrayInputStream("no es un zip".toByteArray())) }.isSuccess)
        assertTrue(ctx.getSharedPreferences("eq2", Context.MODE_PRIVATE).getBoolean("enabled", false))
    }

    @Test
    fun ignoresUnknownOrDangerousPaths() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun put(name: String, data: String) { z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray()); z.closeEntry() }
                put("c-eq-backup.json", """{"format":1}""")
                put("files/skin-windows/../../evil.txt", "x")
                put("files/otro.txt", "x")
                put("prefs/sistema.json", "{}")
            }
        }.toByteArray()
        Backup.import(ctx, ByteArrayInputStream(zip))
        assertFalse(File(ctx.filesDir.parentFile, "evil.txt").exists())
        assertFalse(File(ctx.filesDir, "otro.txt").exists())
    }
}
