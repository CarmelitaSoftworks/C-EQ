package cl.ecualizador

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cálculos del ecualizador, sin dependencias de Android para poder probarlos.
 * Diseño inspirado en Equalizer314 (DynamicsProcessing sobre la sesión 0); el código es propio.
 */
object EqCore {
    /** Centros de las 10 bandas de un ecualizador gráfico por octavas (Hz). */
    val CENTERS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val BANDS = CENTERS.size
    const val MIN_DB = -12f
    const val MAX_DB = 12f

    /** Refuerzo de graves al 100 %, repartido sobre las bandas bajas. */
    const val BASS_MAX_DB = 9f
    private val BASS_WEIGHTS = floatArrayOf(1f, 0.85f, 0.5f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f)

    /**
     * Frecuencias de corte para DynamicsProcessing: cada banda cubre desde el corte anterior hasta el
     * suyo, así que se usa el punto medio geométrico entre centros vecinos (y 20 kHz para la última).
     */
    fun cutoffs(): FloatArray = FloatArray(BANDS) { i ->
        if (i == BANDS - 1) 20000f else sqrt(CENTERS[i] * CENTERS[i + 1])
    }

    fun label(i: Int): String {
        val c = CENTERS[i]
        return if (c < 1000f) c.toInt().toString() else "${(c / 1000f).let { if (it % 1f == 0f) it.toInt() else it }}k"
    }

    /** Ganancia final de cada banda: la curva elegida más el refuerzo de graves (0..1). */
    fun finalGains(levels: List<Float>, bass: Float): List<Float> = List(BANDS) { i ->
        (levels.getOrElse(i) { 0f } + bass.coerceIn(0f, 1f) * BASS_MAX_DB * BASS_WEIGHTS[i]).coerceIn(-15f, 21f)
    }

    /** Preamplificador negativo que compensa la banda más alta, para no saturar (como Equalizer APO). */
    fun autoPreamp(gains: List<Float>): Float = -(gains.maxOrNull() ?: 0f).coerceAtLeast(0f)

    /**
     * Cuánto volumen se baja para que subir bandas no sature. Con DynamicsProcessing hay además un
     * limitador que impide la saturación digital, así que no hace falta bajar todo lo que sube la
     * banda más alta (eso es lo que hace perder volumen al subir los graves).
     */
    enum class Headroom(@androidx.annotation.StringRes val label: Int, @androidx.annotation.StringRes val description: Int) {
        FULL(R.string.headroom_full, R.string.headroom_full_desc),
        BALANCED(R.string.headroom_balanced, R.string.headroom_balanced_desc),
        LOUD(R.string.headroom_loud, R.string.headroom_loud_desc),
    }

    /** Dentro de este margen subir una banda no resta volumen en el modo equilibrado. */
    const val FREE_HEADROOM_DB = 3f

    /**
     * Preamplificador (≤ 0 dB) según el modo. Sin limitador (ecualizador básico) solo se puede evitar
     * la saturación bajando todo, salvo que se elija «Volumen».
     */
    fun preamp(gains: List<Float>, mode: Headroom, hasLimiter: Boolean): Float {
        val peak = -autoPreamp(gains)
        return when (mode) {
            Headroom.FULL -> -peak
            Headroom.BALANCED -> if (hasLimiter) -((peak - FREE_HEADROOM_DB).coerceAtLeast(0f) / 2) else -peak
            Headroom.LOUD -> 0f
        }
    }

    /** Valor de la curva en una frecuencia cualquiera (interpolación lineal en escala logarítmica). */
    fun interpolate(levels: List<Float>, freqHz: Float): Float {
        if (freqHz <= CENTERS.first()) return levels.first()
        if (freqHz >= CENTERS.last()) return levels.last()
        val i = CENTERS.indexOfLast { it <= freqHz }
        val t = (ln(freqHz) - ln(CENTERS[i])) / (ln(CENTERS[i + 1]) - ln(CENTERS[i]))
        return levels[i] + (levels[i + 1] - levels[i]) * t
    }

    /** Potenciar volumen: ganancia extra de entrada, contenida por el limitador. */
    const val BOOST_MAX_DB = 6f

    /**
     * Limitador al final de la cadena: desde −2 dBFS, 30:1 (casi un techo) y reacción de medio
     * milisegundo, para que los picos no lleguen a recortarse antes de que actúe.
     */
    const val LIMITER_THRESHOLD_DB = -2f
    const val LIMITER_RATIO = 30f
    const val LIMITER_ATTACK_MS = 0.5f
    const val LIMITER_RELEASE_MS = 80f

    /**
     * Protección del altavoz del teléfono: no puede reproducir graves profundos, y subirlos solo lo
     * satura. Tope por banda (31 y 62 Hz sin subir, 125 Hz hasta +2 dB, 250 Hz hasta +4 dB) y poco
     * volumen extra; la protección contra distorsión pasa a «Máxima».
     */
    private val SPEAKER_MAX_DB = floatArrayOf(0f, 0f, 2f, 4f, MAX_DB, MAX_DB, MAX_DB, MAX_DB, MAX_DB, MAX_DB)
    const val SPEAKER_BOOST_MAX_DB = 2f

    fun protectSpeaker(gains: List<Float>): List<Float> = List(BANDS) { i -> minOf(gains.getOrElse(i) { 0f }, SPEAKER_MAX_DB[i]) }

    /** Atenuación máxima del canal contrario cuando el balance está en un extremo. */
    const val BALANCE_RANGE_DB = 24f

    /**
     * Ganancia de entrada por canal (izquierdo, derecho): preamplificador + refuerzo y el balance
     * (−1 todo a la izquierda, +1 todo a la derecha), atenuando solo el canal contrario.
     */
    fun channelGains(preamp: Float, boost: Float, balance: Float): Pair<Float, Float> {
        val base = preamp + boost.coerceIn(0f, BOOST_MAX_DB)
        val b = balance.coerceIn(-1f, 1f)
        val left = base - if (b > 0) b * BALANCE_RANGE_DB else 0f
        val right = base - if (b < 0) -b * BALANCE_RANGE_DB else 0f
        return left to right
    }

    /** Puntos de la curva de respuesta entre 20 Hz y 20 kHz para dibujarla (frecuencia, dB). */
    fun responseCurve(gains: List<Float>, points: Int = 64): List<Pair<Float, Float>> = List(points) { i ->
        val f = 20f * (1000f).pow(i / (points - 1f))
        f to interpolate(gains, f)
    }

    /**
     * Compensación de volumen bajo (loudness): el oído pierde graves y agudos a poco volumen
     * (curvas de igual sonoridad, ISO 226). Nada sobre 60 % de volumen; completa al 10 %.
     */
    private val LOUDNESS_SHAPE = floatArrayOf(6f, 5f, 3.5f, 1.5f, 0f, 0f, 0f, 0.5f, 1.5f, 2.5f)

    fun loudness(volumeFraction: Float): List<Float> {
        val k = ((0.6f - volumeFraction.coerceIn(0f, 1f)) / 0.5f).coerceIn(0f, 1f)
        return LOUDNESS_SHAPE.map { it * k }
    }

    /** Paso intermedio de una transición entre dos curvas (t de 0 a 1, con aceleración suave). */
    fun ramp(from: List<Float>, to: List<Float>, t: Float): List<Float> {
        val x = t.coerceIn(0f, 1f)
        val e = x * x * (3 - 2 * x)
        return List(BANDS) { i -> from.getOrElse(i) { 0f } + (to.getOrElse(i) { 0f } - from.getOrElse(i) { 0f }) * e }
    }

    /** Exporta la curva en formato GraphicEQ de Equalizer APO (PC) y compatibles. */
    fun toGraphicEq(levels: List<Float>): String =
        "GraphicEQ: " + CENTERS.indices.joinToString("; ") { i ->
            "${CENTERS[i].toInt()} ${"%.1f".format(java.util.Locale.US, levels.getOrElse(i) { 0f })}"
        }

    /**
     * Importa una curva GraphicEQ («GraphicEQ: 20 -1.2; 21 -1.1; …», p. ej. de AutoEQ) con cualquier
     * cantidad de puntos, llevándola a las 10 bandas. Devuelve null si el texto no tiene ese formato.
     */
    fun fromGraphicEq(text: String): List<Float>? {
        val body = Regex("""GraphicEQ:\s*(.+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: return null
        val points = body.split(';').mapNotNull { part ->
            val nums = part.trim().split(Regex("""\s+""")).mapNotNull { it.replace(',', '.').toFloatOrNull() }
            if (nums.size >= 2 && nums[0] > 0f) nums[0] to nums[1] else null
        }.sortedBy { it.first }
        if (points.size < 2) return null
        return CENTERS.map { f ->
            val v = when {
                f <= points.first().first -> points.first().second
                f >= points.last().first -> points.last().second
                else -> {
                    val j = points.indexOfLast { it.first <= f }
                    val (f0, g0) = points[j]; val (f1, g1) = points[j + 1]
                    val t = (ln(f) - ln(f0)) / (ln(f1) - ln(f0))
                    g0 + (g1 - g0) * t
                }
            }
            (Math.round(v.coerceIn(MIN_DB, MAX_DB) * 2) / 2f)
        }
    }

    val PRESETS: Map<String, List<Float>> = linkedMapOf(
        "Plano" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Graves extra" to listOf(7f, 6f, 4.5f, 2f, 0f, 0f, 0f, 0f, 0.5f, 1f),
        "Graves y agudos" to listOf(5f, 4f, 2f, 0f, -1.5f, -1.5f, 0f, 2f, 4f, 5f),
        "Fiesta" to listOf(6f, 5f, 2.5f, 0f, -1f, 0f, 1f, 2.5f, 4f, 4.5f),
        "Rock" to listOf(4.5f, 3.5f, 2f, 0.5f, -1f, -0.5f, 1.5f, 3f, 3.5f, 4f),
        "Pop" to listOf(-1f, 0f, 1.5f, 3f, 3.5f, 2.5f, 1f, 0f, 0.5f, 1f),
        "Voces" to listOf(-2f, -1.5f, -1f, 0.5f, 2.5f, 3.5f, 3f, 2f, 0.5f, -1f),
        "Clásica" to listOf(3f, 2.5f, 1.5f, 1f, 0f, 0f, -0.5f, 1f, 2f, 3f),
        "Podcast" to listOf(-4f, -3f, -1f, 1f, 2.5f, 3f, 2.5f, 1.5f, 0f, -1.5f),
        "Volumen bajo" to listOf(5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 1f, 2.5f, 3.5f),
        "Electrónica" to listOf(6f, 5f, 3f, 0f, -1.5f, -0.5f, 1f, 2f, 3.5f, 4f),
        "Hip-hop" to listOf(6f, 5.5f, 3.5f, 1f, -1f, -1f, 0.5f, 1f, 2f, 2.5f),
        "Reggaetón" to listOf(7f, 6f, 3f, 0.5f, -1f, 0f, 1f, 1.5f, 2.5f, 3f),
        "Metal" to listOf(5f, 4f, 1.5f, -1f, -2.5f, -1f, 2f, 4f, 4.5f, 4f),
        "Jazz" to listOf(3f, 2.5f, 1.5f, 1.5f, -1f, -1f, 0f, 1.5f, 2.5f, 3f),
        "Acústica" to listOf(2f, 2f, 1.5f, 1f, 0.5f, 1f, 1.5f, 2f, 2f, 1.5f),
        "R&B" to listOf(5f, 5f, 3f, 0f, -1f, 0.5f, 1.5f, 1f, 1.5f, 2.5f),
        "Latina" to listOf(4f, 3f, 1f, 0f, 0f, 1f, 2f, 2.5f, 2.5f, 2f),
        "Cine" to listOf(4f, 3.5f, 2f, 0f, 0f, 1.5f, 2.5f, 2f, 1.5f, 2f),
        "Noche" to listOf(-3f, -2f, -1f, 0f, 1f, 1.5f, 1.5f, 0.5f, -1f, -2f),
        "Menos graves" to listOf(-6f, -5f, -3.5f, -1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Agudos extra" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 4f, 6f, 7f),
        "Parlante pequeño" to listOf(-5f, -2f, 2f, 2.5f, 1f, 0f, 1f, 1.5f, 1f, 0f),
    )

    /** Nombre de cada preajuste de la app en el idioma del teléfono (se guardan con su nombre original). */
    val PRESET_LABELS: Map<String, Int> = mapOf(
        "Plano" to R.string.preset_flat,
        "Graves extra" to R.string.preset_bass_extra,
        "Graves y agudos" to R.string.preset_bass_treble,
        "Fiesta" to R.string.preset_party,
        "Rock" to R.string.preset_rock,
        "Pop" to R.string.preset_pop,
        "Voces" to R.string.preset_vocals,
        "Clásica" to R.string.preset_classical,
        "Podcast" to R.string.preset_podcast,
        "Volumen bajo" to R.string.preset_low_volume,
        "Automático" to R.string.preset_automatic,
        "Calibrado" to R.string.preset_calibrated,
        "Importado" to R.string.preset_imported,
        "Electrónica" to R.string.preset_electronic,
        "Hip-hop" to R.string.preset_hiphop,
        "Reggaetón" to R.string.preset_reggaeton,
        "Metal" to R.string.preset_metal,
        "Jazz" to R.string.preset_jazz,
        "Acústica" to R.string.preset_acoustic,
        "R&B" to R.string.preset_rnb,
        "Latina" to R.string.preset_latin,
        "Cine" to R.string.preset_movies,
        "Noche" to R.string.preset_night,
        "Menos graves" to R.string.preset_less_bass,
        "Agudos extra" to R.string.preset_more_treble,
        "Parlante pequeño" to R.string.preset_small_speaker,
    )

    fun presetLabel(ctx: android.content.Context, name: String): String = PRESET_LABELS[name]?.let { ctx.getString(it) } ?: name

    /**
     * Preajustes de Winamp (.eqf y winamp.q1): cabecera «Winamp EQ library file v1.1», «\u001A!--» y
     * entradas de 257 bytes de nombre más 11 valores (10 bandas y preamplificador) de 0 (+12 dB) a 63
     * (−12 dB). Las bandas de Winamp (60 Hz–16 kHz) se toman como las 10 de la app, con el
     * preamplificador sumado. Vacía si no es un archivo de Winamp.
     */
    fun parseWinampEq(bytes: ByteArray): List<Pair<String, List<Float>>> {
        val header = "Winamp EQ library file v1.1".toByteArray(Charsets.US_ASCII)
        if (bytes.size < header.size + 4 || !bytes.copyOfRange(0, header.size).contentEquals(header)) return emptyList()
        var i = header.size + 4
        val out = mutableListOf<Pair<String, List<Float>>>()
        while (i + 268 <= bytes.size) {
            val nameBytes = bytes.copyOfRange(i, i + 257)
            val end = nameBytes.indexOf(0.toByte()).let { if (it < 0) 257 else it }
            val name = String(nameBytes, 0, end, Charsets.ISO_8859_1).trim()
            fun db(v: Int) = 12f - (v and 0xFF).coerceIn(0, 63) * 24f / 63f
            // El preamplificador de Winamp (el byte 11) sube o baja todo: se suma a cada banda
            val preamp = db(bytes[i + 257 + 10].toInt())
            val levels = List(BANDS) { b ->
                Math.round((db(bytes[i + 257 + b].toInt()) + preamp).coerceIn(MIN_DB, MAX_DB) * 2) / 2f
            }
            if (name.isNotEmpty()) out += name to levels
            i += 268
        }
        return out
    }
}
