package cl.ecualizador

/**
 * Medidas del skin completo hacia abajo, como se apilaban las ventanas de Winamp 2: reproductor,
 * ecualizador y lista de reproducción, con la lista del alto que quepa (en tramos de 29 px).
 */
object SkinStack {
    /** Reproductor y ecualizador, en píxeles del skin. */
    const val FIXED = WinampWindow.HEIGHT * 2
    const val MAX_SIDES = 8

    /** Escala del skin para el espacio dado: entera si cabe al doble o más, para que se vea nítido. */
    fun scale(w: Float, h: Float): Float {
        val fit = minOf(w / WinampWindow.WIDTH, h / (FIXED + WinampWindow.playlistHeight(1)))
        return if (fit >= 2f) kotlin.math.floor(fit) else fit
    }

    /** Tramos de la lista que caben en [h] a esa escala. */
    fun sides(h: Float, scale: Float): Int =
        ((h / scale - FIXED - WinampWindow.PL_TOP_H - WinampWindow.PL_BOTTOM_H) / WinampWindow.PL_SIDE_H).toInt().coerceIn(1, MAX_SIDES)

    /** Alto total para [width] sin pasar de [maxHeight]. */
    fun height(width: Float, maxHeight: Float): Float {
        val scale = scale(width, maxHeight)
        return (FIXED + WinampWindow.playlistHeight(sides(maxHeight, scale))) * scale
    }
}
