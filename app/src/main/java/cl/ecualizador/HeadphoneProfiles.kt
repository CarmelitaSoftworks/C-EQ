package cl.ecualizador

/**
 * Curvas de corrección de AutoEQ (github.com/jaakkopasanen/AutoEq, MIT) para audífonos Bluetooth
 * populares, llevadas a las 10 bandas y centradas en los medios. Generado por tools/gen_autoeq.py;
 * no editar a mano. Se reconoce el modelo por el nombre Bluetooth.
 */
object HeadphoneProfiles {
    /** [source]: quién midió los audífonos (según AutoEQ). */
    class Profile(val model: String, val source: String, val pattern: Regex, val levels: FloatArray)

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val ALL: List<Profile> = listOf(
        Profile("Sony WH-1000XM3", "oratory1990", r("""wh-?1000 ?xm3"""), floatArrayOf(-5.5f, -5.5f, -8.0f, -4.0f, -3.5f, 0.5f, 2.5f, 3.0f, -1.0f, 0.5f)),
        Profile("Sony WH-1000XM4", "oratory1990", r("""wh-?1000 ?xm4"""), floatArrayOf(-6.0f, -6.0f, -8.5f, -5.0f, -2.5f, -0.5f, 3.0f, -2.5f, -1.5f, -4.0f)),
        Profile("Sony WH-1000XM5", "oratory1990", r("""wh-?1000 ?xm5"""), floatArrayOf(-6.0f, -6.0f, -8.0f, -6.5f, -2.0f, 0.0f, 2.0f, -3.0f, -1.0f, 1.5f)),
        Profile("Sony WF-1000XM3", "oratory1990", r("""wf-?1000 ?xm3"""), floatArrayOf(3.0f, 1.0f, -2.5f, -2.5f, -1.0f, 0.5f, 1.0f, 1.5f, 1.0f, -5.0f)),
        Profile("Sony WF-1000XM4", "oratory1990", r("""wf-?1000 ?xm4"""), floatArrayOf(-0.5f, -1.5f, -3.5f, -3.0f, -2.0f, 0.0f, 2.0f, 2.5f, 6.5f, 6.5f)),
        Profile("Sony WF-1000XM5", "oratory1990", r("""wf-?1000 ?xm5"""), floatArrayOf(1.0f, 0.0f, -2.5f, -2.0f, 0.5f, 1.0f, -1.0f, 1.0f, 1.0f, 3.5f)),
        Profile("Sony WH-CH720N", "Rtings on Bruel & Kjaer 5128", r("""wh-?ch720"""), floatArrayOf(-3.5f, -3.5f, -7.0f, -5.0f, 0.0f, 1.0f, -1.0f, 1.5f, -4.5f, 4.0f)),
        Profile("Sony WH-CH520", "Rtings on HMS II.3", r("""wh-?ch520"""), floatArrayOf(3.5f, -1.5f, -0.5f, -0.5f, 0.0f, -1.0f, 1.0f, 2.5f, -0.5f, -3.0f)),
        Profile("Apple Airpods Pro 2", "HypetheSonics on GRAS RA0045", r("""airpods pro 2|airpods pro \(2"""), floatArrayOf(-1.0f, 1.5f, 0.0f, -1.5f, -1.5f, 0.0f, 1.5f, 1.0f, 0.5f, 0.0f)),
        Profile("Apple AirPods Max", "oratory1990", r("""airpods max"""), floatArrayOf(-2.5f, -1.5f, -1.5f, -2.0f, -1.0f, -1.5f, 2.5f, 5.0f, 4.0f, -5.0f)),
        Profile("Apple AirPods 3", "Super Review", r("""airpods 3\b|airpods \(3"""), floatArrayOf(6.0f, 2.0f, -1.0f, -0.5f, 1.5f, 0.5f, -2.0f, 2.0f, 1.5f, 0.5f)),
        Profile("Samsung Galaxy Buds2 Pro", "oratory1990", r("""galaxy buds ?2 pro"""), floatArrayOf(-2.5f, -0.5f, -1.0f, -1.0f, -0.5f, 0.5f, 0.0f, -1.5f, 1.5f, -4.5f)),
        Profile("Samsung Galaxy Buds2", "oratory1990", r("""galaxy buds ?2\b(?! ?pro)"""), floatArrayOf(0.0f, -0.5f, -1.5f, -1.5f, -1.0f, 0.5f, 0.5f, 3.5f, -0.5f, 2.5f)),
        Profile("Samsung Galaxy Buds Pro", "oratory1990", r("""galaxy buds pro"""), floatArrayOf(-1.5f, -2.0f, -4.0f, -3.5f, -1.5f, 1.0f, 0.5f, -0.5f, -3.5f, -7.0f)),
        Profile("Samsung Galaxy Buds Live", "HypetheSonics", r("""galaxy buds live"""), floatArrayOf(1.0f, -1.5f, -3.5f, 0.0f, 0.0f, -0.5f, 0.5f, 2.0f, 4.0f, 6.5f)),
        Profile("Samsung Galaxy Buds FE", "Regan Cipher", r("""galaxy buds fe"""), floatArrayOf(-2.0f, -2.0f, -1.5f, -2.0f, -1.0f, 1.0f, 0.5f, 0.5f, -1.5f, -3.5f)),
        Profile("Samsung Galaxy Buds", "oratory1990", r("""galaxy buds( \(|$)"""), floatArrayOf(1.5f, 1.0f, 0.0f, -0.5f, 0.0f, 0.5f, -0.5f, 1.5f, 2.0f, 6.0f)),
        Profile("Bose QuietComfort Ultra Headphones", "Rtings on Bruel & Kjaer 5128", r("""(quietcomfort|qc) ultra headphones"""), floatArrayOf(-3.0f, -2.0f, 0.0f, 1.5f, 0.0f, 0.0f, 0.5f, 1.5f, 1.0f, 4.0f)),
        Profile("Bose QuietComfort Earbuds", "Rtings on HMS II.3", r("""(quietcomfort|qc) earbuds"""), floatArrayOf(1.5f, 2.5f, 1.0f, 2.0f, 1.0f, 0.5f, -1.5f, 1.0f, -0.5f, -1.0f)),
        Profile("Bose QuietComfort 45", "oratory1990", r("""(quietcomfort|qc) ?45"""), floatArrayOf(-2.0f, -2.0f, -2.5f, -1.5f, 0.5f, 1.0f, -1.5f, -0.5f, 1.5f, -6.5f)),
        Profile("Bose QuietComfort 35 II", "crinacle on GRAS 43AG-7", r("""(quietcomfort|qc) ?35"""), floatArrayOf(-1.0f, -2.5f, -3.5f, -4.0f, -1.5f, 1.0f, 0.0f, -3.0f, 2.0f, 0.5f)),
        Profile("JBL Tune 510BT", "Rtings on HMS II.3", r("""tune ?510"""), floatArrayOf(-2.5f, -6.5f, -4.0f, -4.0f, -0.5f, -1.0f, 1.5f, -4.5f, -5.0f, -7.5f)),
        Profile("JBL Tune 520BT", "Rtings on Bruel & Kjaer 5128", r("""tune ?520"""), floatArrayOf(4.5f, -0.5f, -1.0f, -1.0f, 0.0f, -1.5f, 1.5f, -2.0f, 1.5f, 2.5f)),
        Profile("JBL Tune 760NC", "Rtings on Bruel & Kjaer 5128", r("""tune ?760"""), floatArrayOf(-7.5f, -5.0f, -3.5f, -3.0f, -1.0f, 0.0f, 1.0f, -1.0f, -3.0f, -3.0f)),
        Profile("JBL Live 660NC", "Rtings on HMS II.3", r("""live ?660"""), floatArrayOf(-1.5f, -4.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 1.5f, -2.0f, -5.0f)),
        Profile("JBL Tune 230NC TWS", "Rtings on HMS II.3", r("""tune ?230"""), floatArrayOf(-6.5f, -4.5f, -3.5f, -0.5f, 0.5f, 0.0f, -0.5f, 1.0f, -2.0f, -8.0f)),
        Profile("JBL Tune 720BT", "Filk", r("""tune ?720"""), floatArrayOf(7.0f, 5.0f, 2.5f, 1.0f, -2.5f, 1.5f, 0.5f, 3.0f, 0.0f, -6.0f)),
        Profile("Sennheiser Momentum 4 Wireless", "oratory1990", r("""momentum 4\b"""), floatArrayOf(-10.0f, -7.0f, -6.5f, -4.5f, -1.5f, -2.0f, 4.0f, -1.0f, -1.0f, -2.5f)),
        Profile("Sennheiser Momentum True Wireless 3", "oratory1990", r("""momentum (true wireless|tw) ?3"""), floatArrayOf(-2.0f, -1.0f, -1.5f, -1.5f, 0.0f, 0.0f, 0.5f, 5.5f, 1.0f, -4.0f)),
        Profile("Beats Studio Buds", "oratory1990", r("""studio buds(?! ?\+)"""), floatArrayOf(1.0f, 3.5f, 4.0f, 5.0f, 1.5f, 0.0f, -1.5f, 3.0f, 4.5f, 3.5f)),
        Profile("Beats Fit Pro", "Rtings on HMS II.3", r("""beats fit pro"""), floatArrayOf(-1.5f, -1.0f, 1.0f, 1.5f, 1.5f, 0.0f, -1.5f, 0.5f, 0.0f, -0.5f)),
        Profile("Beats Powerbeats Pro", "oratory1990", r("""powerbeats pro"""), floatArrayOf(0.0f, 0.5f, -0.5f, 1.0f, 1.0f, -0.5f, -0.5f, 3.0f, 3.5f, 4.0f)),
        Profile("Google Pixel Buds Pro", "HypetheSonics on GRAS RA0045", r("""pixel buds pro"""), floatArrayOf(-1.5f, -1.0f, 0.5f, 0.0f, 0.5f, 0.5f, -1.0f, -2.5f, 4.5f, 2.0f)),
        Profile("Google Pixel Buds A-Series", "Rtings on HMS II.3", r("""pixel buds a"""), floatArrayOf(4.0f, 4.0f, 3.0f, 2.0f, -1.0f, 0.0f, 1.0f, -3.0f, -3.5f, -5.0f)),
        Profile("Nothing ear (1)", "DHRME", r("""nothing ear ?\(1\)"""), floatArrayOf(-2.5f, -0.5f, -2.5f, -3.0f, -4.0f, 2.5f, 2.0f, -1.5f, 0.0f, 0.0f)),
        Profile("Nothing ear (2)", "Super Review", r("""nothing ear ?\(2\)"""), floatArrayOf(0.0f, -1.5f, -0.5f, 1.0f, -1.0f, 0.0f, 0.5f, 2.0f, 2.5f, 6.5f)),
        Profile("Anker Soundcore Life Q30", "Kuulokenurkka", r("""life ?q30"""), floatArrayOf(-3.0f, -5.5f, -5.5f, 1.0f, -4.0f, 3.0f, 1.0f, 5.0f, 3.5f, -5.0f)),
        Profile("Anker Soundcore Liberty Air 2 Pro", "Rtings on HMS II.3", r("""liberty air 2 pro"""), floatArrayOf(-7.0f, -6.5f, -5.0f, -1.0f, -2.0f, 1.0f, 1.0f, 1.0f, -8.0f, 0.5f)),
        Profile("Jabra Elite 75t", "oratory1990", r("""elite ?75t"""), floatArrayOf(-7.5f, -7.0f, 3.0f, 4.0f, 1.5f, 0.5f, -2.0f, 2.0f, -1.5f, -4.0f)),
        Profile("Jabra Elite 85t", "Rtings on HMS II.3", r("""elite ?85t"""), floatArrayOf(-4.0f, -3.5f, -1.5f, -1.5f, 0.0f, 0.5f, -0.5f, 2.0f, 1.0f, 1.5f)),
        Profile("Jabra Elite 7 Pro", "oratory1990", r("""elite ?7 pro"""), floatArrayOf(-2.0f, -2.0f, -1.0f, 0.0f, 2.0f, -0.5f, -2.0f, -0.5f, -4.0f, -2.5f)),
        Profile("Jabra Elite 85h", "Rtings on HMS II.3", r("""elite ?85h"""), floatArrayOf(-2.0f, -1.0f, -0.5f, 0.0f, -0.5f, -0.5f, 1.0f, 0.5f, -2.0f, -7.5f)),
        Profile("Audio-Technica ATH-M50xBT2", "Rtings on HMS II.3", r("""m50x ?bt ?2"""), floatArrayOf(2.5f, 1.0f, -2.0f, 4.0f, 1.0f, -1.5f, 0.0f, 3.0f, -4.0f, -7.5f)),
        Profile("Audio-Technica ATH-M50xBT", "crinacle on GRAS 43AG-7", r("""m50x ?bt"""), floatArrayOf(-0.5f, -1.5f, -6.5f, -3.5f, 3.5f, -1.5f, -2.0f, -2.5f, -0.5f, -4.5f)),
        Profile("Huawei Freebuds Pro", "Regan Cipher", r("""freebuds pro(?! ?\d)"""), floatArrayOf(3.0f, -1.5f, -4.5f, -3.5f, -1.0f, 0.5f, 0.5f, 1.5f, 2.5f, 0.0f)),
        Profile("QCY T13", "Regan Cipher", r("""qcy[- ]?t13"""), floatArrayOf(-6.0f, -7.5f, -5.0f, -4.5f, -0.5f, 0.5f, 0.0f, 5.0f, -2.0f, -1.0f)),
        Profile("Edifier W820NB", "Kuulokenurkka", r("""w820nb"""), floatArrayOf(-4.5f, -7.5f, -7.0f, -1.5f, 0.0f, -1.0f, 0.5f, 2.5f, -1.0f, 1.0f)),
        Profile("Skullcandy Crusher Evo", "Rtings on HMS II.3", r("""crusher evo"""), floatArrayOf(-3.5f, -4.0f, -6.0f, -5.0f, -0.5f, -1.5f, 2.0f, -1.5f, -10.0f, -8.5f)),
    )

    /** Curva del modelo de audífonos con ese nombre Bluetooth, o null si no está. */
    fun match(deviceName: String?): Profile? =
        deviceName?.takeIf { it.isNotBlank() }?.let { n -> ALL.firstOrNull { it.pattern.containsMatchIn(n) } }
}
