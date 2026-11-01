package cl.ecualizador

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Ventanas de los skins clásicos (atlas de 275 de ancho, ver [WinampWindow]): las de los skins incluidos vienen en
 * assets/skins/<archiveId>.png y las de los importados en los archivos de la app. Se guardan unas
 * pocas decodificadas, porque el visor las pide en cada cuadro.
 */
object WinampWindows {
    private var app: Context? = null
    private var assetNames: Set<String>? = null
    private val cache = object : LinkedHashMap<String, Bitmap?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?) = size > 6
    }

    fun init(ctx: Context) {
        if (app == null) app = ctx.applicationContext
    }

    /** Ruta de la ventana del skin (archivo o asset), o null si no tiene. */
    fun sourceOf(skin: WinampSkin): String? {
        skin.windowFile?.let { return it }
        if (skin.group != WinampSkins.GROUP_CLASSIC) return null
        val names = assetNames ?: app?.let { c ->
            runCatching { c.assets.list("skins")?.toSet() }.getOrNull().orEmpty().also { assetNames = it }
        } ?: return null
        val file = "${skin.archiveId}.png"
        return if (file in names) "asset:skins/$file" else null
    }

    /** La ventana del skin importado es de un formato anterior (sin las piezas en vivo) o no existe. */
    fun isOutdated(skin: WinampSkin): Boolean {
        val f = skin.windowFile ?: return true
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f, o)
        return o.outHeight != WinampWindow.ATLAS_HEIGHT
    }

    private val ACCEPTED_HEIGHTS = setOf(WinampWindow.ATLAS_HEIGHT, WinampWindow.LIVE_HEIGHT, WinampWindow.HEIGHT)

    @Synchronized
    fun clear() = cache.clear()

    /**
     * Atlas para el ecualizador y la lista de [skin]: el suyo si los trae; si no (skin sin EQMAIN.BMP
     * o PLEDIT.BMP, o atlas de un formato anterior), el del skin base, como hace Winamp con lo que
     * le falta a un skin.
     */
    fun extras(skin: WinampSkin): Pair<Bitmap?, Bitmap?> {
        val own = bitmap(skin)?.takeIf { it.height >= WinampWindow.ATLAS_HEIGHT }
        val base by lazy { bitmap(WinampSkins.DEFAULT)?.takeIf { it.height >= WinampWindow.ATLAS_HEIGHT } }
        fun has(b: Bitmap?, y: Int) = b != null && (b.getPixel(0, y) ushr 24) != 0
        val eq = if (has(own, WinampWindow.EQ_Y)) own else base
        val pl = if (has(own, WinampWindow.PL_Y)) own else base
        return eq to pl
    }

    @Synchronized
    fun bitmap(skin: WinampSkin): Bitmap? {
        val src = sourceOf(skin) ?: return null
        if (cache.containsKey(src)) return cache[src]
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = runCatching {
            if (src.startsWith("asset:")) app?.assets?.open(src.removePrefix("asset:"))?.use { BitmapFactory.decodeStream(it, null, opts) }
            else BitmapFactory.decodeFile(src, opts)
        // Atlas completo, sin el ecualizador ni la lista (2.8 a 2.9) o la ventana sola (antes de la 2.8)
        }.getOrNull()?.takeIf { it.width == WinampWindow.WIDTH && it.height in ACCEPTED_HEIGHTS }
        cache[src] = bmp
        return bmp
    }
}
