package cl.ecualizador

/** Qué se está escuchando, para el ajuste automático. */
enum class ListenContent(val label: String) {
    MUSIC("Música"),
    BASS_MUSIC("Música con graves (urbano, electrónica)"),
    ACOUSTIC("Música acústica (clásica, jazz)"),
    SPEECH("Voz (podcast, audiolibro)"),
    VIDEO("Video"),
}

/**
 * Ecualizador automático sin micrófono: parte de la curva del equipo conectado ([AutoProfile]) y la
 * ajusta, en vivo, según lo que suena (música, voz o video) y el volumen:
 *  - a poco volumen el oído pierde graves y agudos (curvas de igual sonoridad, ISO 226): se
 *    compensan;
 *  - a mucho volumen, los parlantes chicos distorsionan con los graves y los audífonos cansan con los
 *    agudos: se recortan un poco.
 * Sin cálculos de Android, para poder probarlo.
 */
object AutoEq {
    private val SPEECH = floatArrayOf(-4f, -3f, -1.5f, 0f, 1f, 2f, 2.5f, 1.5f, 0f, -1f)
    private val VIDEO = floatArrayOf(0f, 0f, -0.5f, -1f, 0f, 1f, 1.5f, 1f, 0f, 0f)
    private val BASS_MUSIC = floatArrayOf(2f, 2f, 1f, 0f, -0.5f, 0f, 0f, 0f, 0.5f, 0.5f)
    private val ACOUSTIC = floatArrayOf(0f, 0.5f, 0.5f, 0f, -0.5f, 0f, 0f, 0.5f, 1f, 1f)
    /** Recorte de graves de los parlantes chicos al máximo volumen. */
    private val SMALL_SPEAKER_LOUD = floatArrayOf(-5f, -4f, -2f, -0.5f, 0f, 0f, 0f, 0f, 0f, 0f)
    /** Agudos más suaves en audífonos al máximo volumen. */
    private val HEADPHONES_LOUD = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -1.5f)

    /** Apps de voz y de video conocidas (por paquete). */
    private val SPEECH_APPS = setOf(
        "au.com.shiftyjelly.pocketcasts", "com.google.android.apps.podcasts", "com.audible.application",
        "fm.castbox.audiobook.radio.podcast", "com.bambuna.podcastaddict", "com.ivoox.app", "grit.storytel.app",
        "com.storytel", "com.podcast.podcasts", "fm.player", "com.spreaker.android", "com.google.android.apps.books",
    )
    private val VIDEO_APPS = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
        "com.wbd.stream", "com.hbo.hbonow", "tv.twitch.android.app", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.instagram.android", "com.facebook.katana", "com.crunchyroll.crunchyroid", "com.google.android.videos",
        "com.apple.atve.androidtv.appletv", "com.paramountplus.android", "com.star.plus", "com.mxtech.videoplayer.ad",
    )

    private val GENRE_SPEECH = Regex("""podcast|spoken|audiobook|audiolibro|talk|news|noticia|habla|speech|comedy""", RegexOption.IGNORE_CASE)
    private val GENRE_BASS = Regex("""rap|hip.?hop|trap|reggae?ton|urban|dembow|drill|electr|edm|house|techno|dub|drum|bass|dance|phonk|funk""", RegexOption.IGNORE_CASE)
    private val GENRE_ACOUSTIC = Regex("""classical|cl[aá]sic|jazz|acoustic|ac[uú]stic|folk|blues|soundtrack|banda sonora|ambient|piano|bolero|opera|ópera""", RegexOption.IGNORE_CASE)

    /** Qué suena, por el género que informa el reproductor o, si no, por la app. */
    fun contentOf(app: String, genre: String): ListenContent = when {
        genre.isNotBlank() && GENRE_SPEECH.containsMatchIn(genre) -> ListenContent.SPEECH
        genre.isNotBlank() && GENRE_ACOUSTIC.containsMatchIn(genre) -> ListenContent.ACOUSTIC
        genre.isNotBlank() && GENRE_BASS.containsMatchIn(genre) -> ListenContent.BASS_MUSIC
        app in SPEECH_APPS -> ListenContent.SPEECH
        app in VIDEO_APPS -> ListenContent.VIDEO
        else -> ListenContent.MUSIC
    }

    private fun small(kind: OutputKind) = kind == OutputKind.PHONE_SPEAKER || kind == OutputKind.BT_PORTABLE_SPEAKER
    private fun headphones(kind: OutputKind) = kind == OutputKind.WIRED_HEADPHONES || kind == OutputKind.BT_HEADPHONES

    /** Curva automática para el equipo [kind], lo que suena y el volumen de la música (0..1). */
    fun curve(kind: OutputKind, content: ListenContent, volume: Float): AutoCurve {
        val base = AutoProfile.curveFor(kind)
        val v = volume.coerceIn(0f, 1f)
        val levels = base.levels.toFloatArray()
        val why = mutableListOf(base.why)
        fun add(shape: FloatArray, k: Float = 1f) { for (i in levels.indices) levels[i] += shape[i] * k }

        when (content) {
            ListenContent.SPEECH -> { add(SPEECH); why += "Suena voz: menos graves y voces más claras." }
            ListenContent.VIDEO -> { add(VIDEO); why += "Suena un video: diálogos más claros." }
            ListenContent.BASS_MUSIC -> { add(BASS_MUSIC); why += "Música con graves: un poco más de bajo." }
            ListenContent.ACOUSTIC -> { add(ACOUSTIC); why += "Música acústica: más aire en los agudos." }
            ListenContent.MUSIC -> {}
        }
        // Volumen bajo: compensación de sonoridad (en la voz solo la mitad, para no embarrarla)
        val loud = EqCore.loudness(v)
        if (loud.any { it > 0f }) {
            val k = if (content == ListenContent.SPEECH) 0.5f else 1f
            for (i in levels.indices) levels[i] += loud[i] * k
            why += "Volumen bajo: más graves y agudos, que el oído pierde a poco volumen."
        }
        // Volumen alto: proteger parlantes chicos y oídos
        val high = ((v - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (high > 0f && small(kind)) { add(SMALL_SPEAKER_LOUD, high); why += "Volumen alto: menos graves profundos para que el parlante no distorsione." }
        if (high > 0f && headphones(kind)) { add(HEADPHONES_LOUD, high); why += "Volumen alto: agudos más suaves para no cansar el oído." }

        val bass = if (content == ListenContent.SPEECH) 0f else base.bass
        return AutoCurve(levels.map { Math.round(it.coerceIn(EqCore.MIN_DB, EqCore.MAX_DB) * 2) / 2f }, bass, why.joinToString(" "))
    }
}
