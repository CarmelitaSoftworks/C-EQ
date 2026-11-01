package cl.ecualizador

/** Reglas del doble toque en el fondo (sin Android, para poder probarlas). */
object WallpaperTouches {
    /**
     * Si dos toques fueron sobre lo mismo: el mismo botón, la misma banda o la misma fila, o los dos
     * sobre la barra de posición o el volumen (el segundo dice adónde).
     */
    fun sameTarget(a: WinampWindow.Hit, b: WinampWindow.Hit): Boolean = when (a) {
        is WinampWindow.Hit.Seek -> b is WinampWindow.Hit.Seek
        is WinampWindow.Hit.Volume -> b is WinampWindow.Hit.Volume
        is WinampWindow.Hit.EqBand -> b is WinampWindow.Hit.EqBand && b.band == a.band
        else -> a == b
    }
}
