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

    /** En pausa, si el título del skin se desplaza, se redibuja solo para moverlo (≈12 cuadros por segundo). */
    const val MARQUEE_FRAME_MS = 80L

    /**
     * Presupuesto de cuadros: cada refresco de pantalla suma su duración y se dibuja cuando alcanza
     * para un cuadro a los [fps] pedidos. Así 60 son 60 también en pantallas de 90 o 120 Hz (a 90 Hz
     * se dibujan 2 de cada 3 refrescos) y 30 son 30 en cualquiera.
     */
    class Pacer {
        private var credit = 1f

        /** Pasó un refresco de [frameMs] milisegundos: si hay que dibujar este cuadro. */
        fun tick(frameMs: Float, fps: Int): Boolean {
            credit = (credit + frameMs * fps.coerceAtLeast(1) / 1000f).coerceAtMost(2f)
            // Un poco de tolerancia: el reloj de los refrescos tiembla y 16,6 ms casi completan uno a 60
            if (credit < 0.95f) return false
            credit = (credit - 1f).coerceAtLeast(0f)
            return true
        }

        /** Al volver a animar, el primer cuadro se dibuja enseguida. */
        fun reset() { credit = 1f }
    }

    /** No suena nada: el reproductor no está reproduciendo y el espectro está en cero. */
    fun isQuiet(values: FloatArray, playing: Boolean): Boolean = !playing && values.all { it < 0.01f }
}
