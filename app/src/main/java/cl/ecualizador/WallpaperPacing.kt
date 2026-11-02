package cl.ecualizador

/**
 * Ritmo del fondo animado, para gastar menos batería (sin Android, para poder probarlo): a 30
 * cuadros por segundo se dibuja uno de cada dos, y si no suena nada un rato el fondo deja de
 * redibujarse hasta que vuelva la música.
 */
object WallpaperPacing {
    /** Cuadros por segundo que se pueden elegir. */
    val FPS_OPTIONS = listOf(30, 60)

    /** Tiempo en silencio antes de dejar de redibujar. */
    const val QUIET_AFTER_MS = 1500L

    /** Cada cuánto se revisa, en pausa, si volvió la música. */
    const val QUIET_CHECK_MS = 500L

    /** Si toca dibujar este cuadro con [fps] y [sinceLastDrawMs] desde el anterior (con margen por el temblor del reloj). */
    fun shouldDraw(fps: Int, sinceLastDrawMs: Long): Boolean = fps >= 60 || sinceLastDrawMs >= 1000L / fps.coerceAtLeast(1) - 4

    /** No suena nada: el reproductor no está reproduciendo y el espectro está en cero. */
    fun isQuiet(values: FloatArray, playing: Boolean): Boolean = !playing && values.all { it < 0.01f }
}
