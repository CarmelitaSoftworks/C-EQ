package cl.ecualizador

import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap

/** Apps que reproducen sonido, para el ajuste por app, y sus nombres. */
object AppNames {
    /** Apps de música conocidas (las de voz y video están en [AutoEq]). */
    private val MUSIC_APPS = setOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.soundcloud.android",
        "com.amazon.mp3", "com.apple.android.music", "com.aspiro.tidal", "com.maxmpz.audioplayer", "org.videolan.vlc",
    )

    /**
     * Apps que se buscan por nombre de paquete. Android 11+ solo deja ver las que el manifiesto
     * declara en queries: AppNamesTest comprueba que estén todas.
     */
    val KNOWN_APPS: Set<String> get() = MUSIC_APPS + AutoEq.KNOWN_APPS

    /** Nombres ya consultados (cada consulta es una llamada al sistema). */
    private val labels = ConcurrentHashMap<String, String>()

    /** Nombre visible de la app [pkg]; si Android no deja verla, el paquete. */
    fun label(ctx: Context, pkg: String): String = labels[pkg] ?: runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.onSuccess { labels[pkg] = it }.getOrDefault(pkg)

    /**
     * Apps de música y video instaladas (las que ofrecen su biblioteca a Android Auto y a los
     * controles multimedia, y las conocidas), más [extra] (la que suena ahora y las que ya tienen
     * ajuste): paquete y nombre, ordenadas por nombre. Hace varias consultas al sistema: se llama
     * fuera del hilo principal.
     */
    fun mediaApps(ctx: Context, extra: Collection<String>): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val browsers = runCatching {
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).map { it.serviceInfo.packageName }
        }.getOrDefault(emptyList())
        val known = KNOWN_APPS.filter { pkg -> labels.containsKey(pkg) || runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess }
        return (browsers + known + extra).filter { it.isNotEmpty() && it != ctx.packageName }
            .distinct().map { it to label(ctx, it) }.sortedBy { it.second.lowercase() }
    }
}
