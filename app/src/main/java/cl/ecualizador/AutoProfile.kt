package cl.ecualizador

/** Tipo de salida de audio, para proponer una curva de partida. */
enum class OutputKind(@androidx.annotation.StringRes val label: Int) {
    PHONE_SPEAKER(R.string.kind_phone_speaker),
    WIRED_HEADPHONES(R.string.kind_wired),
    USB_AUDIO(R.string.kind_usb),
    BT_HEADPHONES(R.string.kind_bt_headphones),
    BT_PORTABLE_SPEAKER(R.string.kind_portable),
    BT_PARTY_SPEAKER(R.string.kind_party),
    BT_CAR(R.string.kind_car),
    BT_TV_SOUNDBAR(R.string.kind_tv),
    BT_UNKNOWN(R.string.kind_bt_unknown),
}

/** Curva propuesta para un tipo de salida, con la explicación que se muestra al usuario. */
/** Un texto de la app con sus datos, para código que no tiene acceso a los recursos de Android. */
data class Txt(@androidx.annotation.StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(ctx: android.content.Context): String = ctx.getString(res, *args.toTypedArray())
}

data class AutoCurve(val levels: List<Float>, val bass: Float, val why: List<Txt>)

/**
 * Clasifica lo conectado por nombre y por la clase Bluetooth que informa el equipo, y propone una
 * curva de partida. Las curvas son orientativas (no hay medición); la calibración con micrófono
 * las afina para parlantes.
 */
object AutoProfile {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val PARTY = r("""\b(gtk|mhc|shake)[- ]|partybox|xboom|\bxl\d|sound ?tower|\bmx-|srs-x[vp]|ult ?tower|boombox|hyperboom|everboom""")
    private val PORTABLE = r("""\bflip ?\d|\bcharge ?\d|\bclip ?\d|\bgo ?\d|srs-x[bge]|ult ?field|wonderboom|megaboom|\bboom ?\d|soundcore (motion|flare|boom)|tribit|emberton|soundlink|\bxtreme|stormbox|\bbluetooth speaker""")
    private val HEADPHONES = r("""\bw[hf]-|\bmdr|airpods|\bbuds|beats|quietcomfort|\bqc ?\d|\btune ?\d|\blive ?\d|liberty|\blife ?[pq]|jabra|sennheiser|momentum|headphone|earbud|\bear ?\(|nothing ear|\bheadset|\btws\b""")
    private val CAR = r("""\bcar\b|carkit|toyota|hyundai|\bkia\b|chevrolet|nissan|\bvw\b|volkswagen|\bford\b|mazda|uconnect|\bsync\b|my ?car|pioneer|kenwood|\bjvc\b|peugeot|renault|suzuki|honda|subaru|mitsubishi""")
    private val TV = r("""\btv\b|soundbar|sound ?bar|\bht-|\bbar ?\d|bravia|\[tv]""")

    // BluetoothClass.Device (valores de la especificación Bluetooth)
    private const val BT_WEARABLE_HEADSET = 0x0404
    private const val BT_HANDSFREE = 0x0408
    private const val BT_LOUDSPEAKER = 0x0414
    private const val BT_HEADPHONES = 0x0418
    private const val BT_PORTABLE_AUDIO = 0x041C
    private const val BT_CAR_AUDIO = 0x0420
    private const val BT_HIFI_AUDIO = 0x0428
    private const val BT_VIDEO_DISPLAY_AND_LOUDSPEAKER = 0x043C

    /** [btDeviceClass] es BluetoothClass.getDeviceClass(), o null si no se conoce. */
    fun classifyBluetooth(name: String, btDeviceClass: Int?): OutputKind {
        // El nombre manda cuando es claro: muchos equipos informan una clase genérica.
        return when {
            // Un modelo de audífonos con curva medida (ver HeadphoneProfiles) es audífono, sin dudas
            HeadphoneProfiles.match(name) != null -> OutputKind.BT_HEADPHONES
            PARTY.containsMatchIn(name) -> OutputKind.BT_PARTY_SPEAKER
            HEADPHONES.containsMatchIn(name) -> OutputKind.BT_HEADPHONES
            CAR.containsMatchIn(name) -> OutputKind.BT_CAR
            TV.containsMatchIn(name) -> OutputKind.BT_TV_SOUNDBAR
            PORTABLE.containsMatchIn(name) -> OutputKind.BT_PORTABLE_SPEAKER
            else -> when (btDeviceClass) {
                BT_HEADPHONES, BT_WEARABLE_HEADSET, BT_HANDSFREE -> OutputKind.BT_HEADPHONES
                BT_CAR_AUDIO -> OutputKind.BT_CAR
                BT_HIFI_AUDIO -> OutputKind.BT_PARTY_SPEAKER
                BT_VIDEO_DISPLAY_AND_LOUDSPEAKER -> OutputKind.BT_TV_SOUNDBAR
                BT_LOUDSPEAKER, BT_PORTABLE_AUDIO -> OutputKind.BT_PORTABLE_SPEAKER
                else -> OutputKind.BT_UNKNOWN
            }
        }
    }

    /**
     * Curva para el equipo: la medida por AutoEQ si se reconoció el modelo de audífonos ([profile], de
     * HeadphoneProfiles.match); si no, la del tipo de equipo.
     */
    fun curveFor(kind: OutputKind, profile: HeadphoneProfiles.Profile?): AutoCurve =
        profile?.let { p ->
            AutoCurve(
                p.levels.toList(), 0f,
                listOf(Txt(R.string.why_autoeq, listOf(p.model, p.source))),
            )
        } ?: curveFor(kind)

    fun curveFor(kind: OutputKind): AutoCurve = when (kind) {
        OutputKind.PHONE_SPEAKER -> AutoCurve(
            listOf(-6f, -5f, -2f, 0f, 1f, 1.5f, 2f, 1.5f, 1f, 0f), 0f,
            listOf(Txt(R.string.why_phone_speaker))
        )
        OutputKind.WIRED_HEADPHONES, OutputKind.BT_HEADPHONES -> AutoCurve(
            listOf(4f, 4f, 3f, 1f, 0f, 0f, 1f, 1f, -1f, -1f), 0f,
            listOf(Txt(R.string.why_headphones))
        )
        OutputKind.BT_PORTABLE_SPEAKER -> AutoCurve(
            listOf(-4f, 0f, 2.5f, 1.5f, 0f, 0f, 0.5f, 1f, 1.5f, 1f), 0.15f,
            listOf(Txt(R.string.why_portable))
        )
        OutputKind.BT_PARTY_SPEAKER -> AutoCurve(
            listOf(1f, 1.5f, 0f, -1.5f, -1f, 0f, 1f, 1.5f, 1.5f, 1f), 0f,
            listOf(Txt(R.string.why_party))
        )
        OutputKind.BT_CAR -> AutoCurve(
            listOf(3f, 3f, 2f, 0f, -1f, 0f, 1f, 2f, 2f, 1f), 0f,
            listOf(Txt(R.string.why_car))
        )
        OutputKind.BT_TV_SOUNDBAR -> AutoCurve(
            listOf(0f, 0f, -0.5f, -1f, 0f, 1.5f, 2.5f, 2f, 0.5f, 0f), 0f,
            listOf(Txt(R.string.why_tv))
        )
        OutputKind.USB_AUDIO, OutputKind.BT_UNKNOWN -> AutoCurve(
            List(EqCore.BANDS) { 0f }, 0f,
            listOf(Txt(R.string.why_unknown))
        )
    }

    /** La calibración con micrófono tiene sentido para parlantes, no para audífonos. */
    fun canCalibrate(kind: OutputKind) = kind !in setOf(OutputKind.WIRED_HEADPHONES, OutputKind.BT_HEADPHONES)
}
