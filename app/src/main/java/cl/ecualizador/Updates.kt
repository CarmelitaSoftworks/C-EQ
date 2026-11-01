package cl.ecualizador

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Versiones nuevas sin permiso de Internet: la app no puede preguntarle a GitHub, así que recuerda
 * buscar cuando la versión instalada ya tiene un tiempo, y lleva a la página de versiones o a
 * Obtainium (una app que revisa GitHub y avisa sola).
 */
object Updates {
    const val RELEASES = "https://github.com/CarmelitaSoftworks/C-EQ/releases/latest"
    private const val REPO = "https://github.com/CarmelitaSoftworks/C-EQ"
    private const val OBTAINIUM_PAGE = "https://github.com/ImranR98/Obtainium"
    /** Días desde la compilación tras los que se recuerda buscar, y cuánto calla «Más tarde». */
    const val REMIND_AFTER_DAYS = 45L
    const val SNOOZE_DAYS = 30L
    private const val PREFS = "updates"

    private fun today() = System.currentTimeMillis() / 86_400_000L

    /** Días desde el último commit de esta versión (0 si no se supo al compilar). */
    fun daysSinceBuild(): Long = if (BuildConfig.BUILD_DAY <= 0) 0 else (today() - BuildConfig.BUILD_DAY).coerceAtLeast(0)

    /** Si toca recordar: la versión es vieja y no se pidió «Más tarde» hace poco. */
    fun shouldRemind(ctx: Context): Boolean =
        shouldRemind(daysSinceBuild(), today(), ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("snoozedUntil", 0L))

    fun shouldRemind(daysSinceBuild: Long, today: Long, snoozedUntil: Long) = daysSinceBuild >= REMIND_AFTER_DAYS && today >= snoozedUntil

    fun snooze(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("snoozedUntil", today() + SNOOZE_DAYS).apply()
    }

    private fun view(ctx: Context, url: String) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** La página de la última versión en el navegador. */
    fun openReleases(ctx: Context) = view(ctx, RELEASES)

    /** Agrega C-EQ a Obtainium para que avise solo; sin Obtainium, abre su página para instalarlo. */
    fun openObtainium(ctx: Context) {
        if (!view(ctx, "obtainium://add/$REPO")) view(ctx, OBTAINIUM_PAGE)
    }
}
