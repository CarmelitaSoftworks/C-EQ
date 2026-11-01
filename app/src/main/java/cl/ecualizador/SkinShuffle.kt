package cl.ecualizador

import android.content.Context
import androidx.annotation.StringRes

/** Cuándo cambiar solo el skin de Winamp por otro al azar. */
enum class SkinShuffle(@StringRes val label: Int) {
    OFF(R.string.shuffle_off),
    DAILY(R.string.shuffle_daily),
    SCREEN_ON(R.string.shuffle_screen_on);

    companion object {
        private const val PREFS = "skin_shuffle"

        fun mode(ctx: Context): SkinShuffle = runCatching {
            valueOf(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", OFF.name)!!)
        }.getOrDefault(OFF)

        fun setMode(ctx: Context, mode: SkinShuffle) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name)
                .putLong("day", today()).apply()
            checkedDay = -1L
        }

        /** Día en la hora del teléfono: el skin diario cambia a medianoche local, no a la de Londres. */
        private fun today() = java.time.LocalDate.now().toEpochDay()

        /** Último día ya revisado en este proceso, para no leer los ajustes cada segundo. */
        @Volatile private var checkedDay = -1L

        /** La pantalla se encendió: otro skin si el modo es ese. */
        fun onScreenOn(ctx: Context) {
            if (mode(ctx) == SCREEN_ON) shuffle(ctx)
        }

        /**
         * Se ve el skin (fondo, app, widget o notificación): otro skin si cambió el día y el modo es
         * diario. Se revisa una vez por día y por proceso.
         */
        fun onShown(ctx: Context) {
            val day = today()
            if (day == checkedDay) return
            checkedDay = day
            if (mode(ctx) != DAILY) return
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getLong("day", 0L) != day) shuffle(ctx)
        }

        @Volatile private var lastShuffle = 0L

        /** Elige otro skin al azar (incluidos e importados), distinto del actual. */
        @Synchronized
        fun shuffle(ctx: Context) {
            // Varios fondos (inicio y bloqueo) reciben el mismo encendido: cuenta una vez
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastShuffle < 3000) return
            lastShuffle = now
            WinampSkins.load(ctx)
            val current = SpectrumPrefs.load(ctx).winampSkin
            val next = pick(WinampSkins.ALL.map { it.name }, current, java.util.Random()) ?: return
            SpectrumPrefs.update(ctx) { it.copy(winampSkin = next) }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("day", today()).apply()
        }

        /** Un nombre al azar de [names] distinto de [current] (null si no hay otro). */
        fun pick(names: List<String>, current: String, random: java.util.Random): String? =
            names.filter { it != current }.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }
}
