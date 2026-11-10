package cl.ecualizador

import android.content.Context
import android.content.Intent

/** Apps que reproducen sonido, para el ajuste por app, y sus nombres. */
object AppNames {
    /** Nombre visible de la app [pkg]; si Android no deja verla, el paquete. */
    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /**
     * Apps de música y video instaladas (las que ofrecen su biblioteca a Android Auto y a los
     * controles multimedia, y las conocidas de [AutoEq]), más la que suena ahora y las que ya tienen
     * ajuste. Ordenadas por nombre.
     */
    fun mediaApps(ctx: Context, extra: Collection<String>): List<String> {
        val pm = ctx.packageManager
        val browsers = runCatching {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).map { it.serviceInfo.packageName }
        }.getOrDefault(emptyList())
        val known = AutoEq.KNOWN_APPS.filter { pkg -> runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess }
        return (browsers + known + extra).filter { it.isNotEmpty() && it != ctx.packageName }
            .distinct().sortedBy { label(ctx, it).lowercase() }
    }
}
