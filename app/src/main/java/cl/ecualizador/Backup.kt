package cl.ecualizador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad de los ajustes en un .zip: las preferencias de la app (perfiles por equipo,
 * preajustes, visor, fondo, reproductor…), los skins importados con su ventana y su .wsz, y la foto
 * del fondo. Al restaurar solo se aceptan esos archivos, con nombres conocidos, y la app se cierra
 * para empezar con los ajustes restaurados.
 */
object Backup {
    private const val MANIFEST = "c-eq-backup.json"
    private const val FORMAT = 1
    /** Preferencias que se guardan (las de la app, no las del sistema). */
    val PREFS = listOf("eq2", "spectrum", "wallpaper", "player", "skin_shuffle", "updates", "welcome")
    private const val SKINS_JSON = "winamp-skins.json"
    private const val WALLPAPER = "wallpaper-original"
    private const val SKIN_DIR = "skin-windows"
    /** Un respaldo no pesa más que esto: la foto del fondo (hasta 80 MB) y los skins, con margen. */
    const val MAX_BYTES = 120L shl 20
    private val SKIN_FILE = Regex("""[A-Za-z0-9._-]{1,80}""")

    class BackupException(message: String) : Exception(message)

    fun export(ctx: Context, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            put(MANIFEST, JSONObject().put("format", FORMAT).put("version", BuildConfig.VERSION_NAME).toString().toByteArray())
            for (name in PREFS) put("prefs/$name.json", prefsToJson(ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all).toString().toByteArray())
            File(ctx.filesDir, SKINS_JSON).takeIf { it.isFile }?.let { put("files/$SKINS_JSON", it.readBytes()) }
            File(ctx.filesDir, WALLPAPER).takeIf { it.isFile }?.let { f ->
                zip.putNextEntry(ZipEntry("files/$WALLPAPER")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            File(ctx.filesDir, SKIN_DIR).listFiles().orEmpty().filter { it.isFile && SKIN_FILE.matches(it.name) }.forEach { f ->
                zip.putNextEntry(ZipEntry("files/$SKIN_DIR/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /**
     * Restaura un respaldo. Primero lo lee entero y lo valida (si algo no cuadra, no se toca nada);
     * después escribe las preferencias y los archivos. Lanza [BackupException] si no es un respaldo.
     */
    fun import(ctx: Context, input: InputStream) {
        val prefs = mutableMapOf<String, JSONObject>()
        val files = mutableMapOf<String, ByteArray>()
        var manifest = false
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val bytes = readLimited(zip, MAX_BYTES - total)
                total += bytes.size
                val name = e.name
                when {
                    name == MANIFEST -> {
                        val format = runCatching { JSONObject(String(bytes)).getInt("format") }.getOrNull()
                        if (format != FORMAT) throw BackupException("format")
                        manifest = true
                    }
                    name.startsWith("prefs/") && name.endsWith(".json") -> {
                        val p = name.removePrefix("prefs/").removeSuffix(".json")
                        if (p in PREFS) prefs[p] = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: throw BackupException("prefs")
                    }
                    name == "files/$SKINS_JSON" || name == "files/$WALLPAPER" -> files[name.removePrefix("files/")] = bytes
                    name.startsWith("files/$SKIN_DIR/") -> {
                        val f = name.removePrefix("files/$SKIN_DIR/")
                        if (SKIN_FILE.matches(f)) files["$SKIN_DIR/$f"] = bytes
                    }
                }
            }
        }
        if (!manifest) throw BackupException("manifest")

        for ((name, json) in prefs) {
            val edit = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            jsonToPrefs(json, edit)
            edit.commit()
        }
        File(ctx.filesDir, SKIN_DIR).listFiles()?.forEach { it.delete() }
        File(ctx.filesDir, SKIN_DIR).mkdirs()
        for ((rel, bytes) in files) {
            // Las rutas de las ventanas de los skins importados apuntan a la carpeta de este teléfono
            val data = if (rel == SKINS_JSON) relocateSkins(ctx, String(bytes)).toByteArray() else bytes
            File(ctx.filesDir, rel).writeBytes(data)
        }
        if (WALLPAPER !in files) File(ctx.filesDir, WALLPAPER).delete()
        if (SKINS_JSON !in files) File(ctx.filesDir, SKINS_JSON).delete()
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw BackupException("size")
        }
        return out.toByteArray()
    }

    /** Lleva las rutas de ventana y .wsz de cada skin importado a la carpeta de este teléfono. */
    internal fun relocateSkins(ctx: Context, text: String): String {
        val dir = File(ctx.filesDir, SKIN_DIR)
        val a = JSONArray(text)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            for (key in listOf("window", "source")) {
                val old = o.optString(key).takeIf { it.isNotEmpty() } ?: continue
                o.put(key, File(dir, File(old).name).path)
            }
        }
        return a.toString()
    }

    /** Preferencias a JSON con su tipo, para restaurarlas tal cual. */
    internal fun prefsToJson(all: Map<String, *>): JSONObject = JSONObject().apply {
        for ((k, v) in all) {
            val (t, value) = when (v) {
                is Boolean -> "b" to v
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                is String -> "s" to v
                is Set<*> -> "ss" to JSONArray(v.map { it.toString() })
                else -> continue
            }
            put(k, JSONObject().put("t", t).put("v", value))
        }
    }

    internal fun jsonToPrefs(json: JSONObject, edit: android.content.SharedPreferences.Editor) {
        for (k in json.keys()) {
            val o = json.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "b" -> edit.putBoolean(k, o.getBoolean("v"))
                "i" -> edit.putInt(k, o.getInt("v"))
                "l" -> edit.putLong(k, o.getLong("v"))
                "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                "s" -> edit.putString(k, o.getString("v"))
                "ss" -> edit.putStringSet(k, o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
    }
}
