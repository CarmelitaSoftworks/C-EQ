"""Genera HeadphoneProfiles.kt a partir de las curvas GraphicEQ de AutoEQ (MIT, jaakkopasanen/AutoEq).

Uso: python3 tools/gen_autoeq.py <carpeta con geq/*.txt y sources.tsv> app/src/main/java/cl/ecualizador/HeadphoneProfiles.kt
Cada curva se lleva a las 10 bandas de la app (interpolación en escala logarítmica, como
EqCore.fromGraphicEq) y se centra en los medios (500 Hz–2 kHz en 0 dB): el volumen lo maneja la
protección contra distorsión de la app.
"""
import math, os, re, sys

D, OUT = sys.argv[1], sys.argv[2]
CENTERS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]

# Nombre Bluetooth de cada modelo (como lo anuncian los audífonos; sin distinguir mayúsculas)
PATTERNS = {
    "Sony WH-1000XM3": r"wh-?1000 ?xm3", "Sony WH-1000XM4": r"wh-?1000 ?xm4", "Sony WH-1000XM5": r"wh-?1000 ?xm5",
    "Sony WF-1000XM3": r"wf-?1000 ?xm3", "Sony WF-1000XM4": r"wf-?1000 ?xm4", "Sony WF-1000XM5": r"wf-?1000 ?xm5",
    "Sony WH-CH720N": r"wh-?ch720", "Sony WH-CH520": r"wh-?ch520",
    # Apple nombra igual varias generaciones («AirPods», «AirPods Pro»): solo se reconoce cuando el
    # nombre dice cuál es, para no aplicar la curva de otro modelo
    "Apple Airpods Pro 2": r"airpods pro 2|airpods pro \(2", "Apple AirPods Max": r"airpods max",
    "Apple AirPods 3": r"airpods 3\b|airpods \(3",
    "Samsung Galaxy Buds2 Pro": r"galaxy buds ?2 pro", "Samsung Galaxy Buds2": r"galaxy buds ?2\b(?! ?pro)",
    "Samsung Galaxy Buds Pro": r"galaxy buds pro", "Samsung Galaxy Buds Live": r"galaxy buds live", "Samsung Galaxy Buds FE": r"galaxy buds fe",
    "Samsung Galaxy Buds": r"galaxy buds( \(|$)",
    "Bose QuietComfort Ultra Headphones": r"(quietcomfort|qc) ultra headphones", "Bose QuietComfort Earbuds": r"(quietcomfort|qc) earbuds",
    "Bose QuietComfort 45": r"(quietcomfort|qc) ?45", "Bose QuietComfort 35 II": r"(quietcomfort|qc) ?35",
    "JBL Tune 510BT": r"tune ?510", "JBL Tune 520BT": r"tune ?520", "JBL Tune 760NC": r"tune ?760", "JBL Live 660NC": r"live ?660",
    "JBL Tune 230NC TWS": r"tune ?230", "JBL Tune 720BT (wireless)": r"tune ?720",
    "Sennheiser Momentum 4 Wireless": r"momentum 4\b", "Sennheiser Momentum True Wireless 3": r"momentum (true wireless|tw) ?3",
    "Beats Studio Buds": r"studio buds(?! ?\+)", "Beats Fit Pro": r"beats fit pro", "Beats Powerbeats Pro": r"powerbeats pro",
    "Google Pixel Buds Pro": r"pixel buds pro", "Google Pixel Buds A-Series": r"pixel buds a",
    "Nothing ear (1)": r"nothing ear ?\(1\)", "Nothing ear (2)": r"nothing ear ?\(2\)",
    "Anker Soundcore Life Q30": r"life ?q30", "Anker Soundcore Liberty Air 2 Pro": r"liberty air 2 pro",
    "Jabra Elite 75t": r"elite ?75t", "Jabra Elite 85t": r"elite ?85t", "Jabra Elite 7 Pro": r"elite ?7 pro", "Jabra Elite 85h": r"elite ?85h",
    "Audio-Technica ATH-M50xBT2": r"m50x ?bt ?2", "Audio-Technica ATH-M50xBT": r"m50x ?bt",
    "Huawei Freebuds Pro": r"freebuds pro(?! ?\d)", "QCY T13": r"qcy[- ]?t13", "Edifier W820NB (ANC off)": r"w820nb",
    "Skullcandy Crusher Evo": r"crusher evo",
}

def bands(text):
    body = re.search(r"GraphicEQ:\s*(.+)", text).group(1)
    pts = sorted((float(a), float(b)) for a, b in (p.strip().split()[:2] for p in body.split(";") if p.strip()))
    def at(f):
        if f <= pts[0][0]: return pts[0][1]
        if f >= pts[-1][0]: return pts[-1][1]
        j = max(i for i, p in enumerate(pts) if p[0] <= f)
        (f0, g0), (f1, g1) = pts[j], pts[j + 1]
        return g0 + (g1 - g0) * (math.log(f) - math.log(f0)) / (math.log(f1) - math.log(f0))
    v = [at(f) for f in CENTERS]
    mid = sum(v[4:7]) / 3
    return [round(max(-12, min(12, x - mid)) * 2) / 2 for x in v]

sources = dict(l.rstrip("\n").split("\t") for l in open(os.path.join(D, "sources.tsv")) if l.strip())
# Los más específicos primero (p. ej. «AirPods Pro 2» antes que «AirPods Pro» y «AirPods»)
order = list(PATTERNS)
rows = []
for model in order:
    f = os.path.join(D, "geq", model + ".txt")
    if model not in sources or not os.path.exists(f): continue
    lv = bands(open(f).read())
    name = model.replace(" (wireless)", "").replace(" (ANC off)", "")
    rows.append('        Profile("%s", "%s", r("""%s"""), floatArrayOf(%s)),' % (
        name, sources[model].replace('"', ""), PATTERNS[model], ", ".join("%sf" % x for x in lv)))

open(OUT, "w").write('''package cl.ecualizador

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
%s
    )

    /** Curva del modelo de audífonos con ese nombre Bluetooth, o null si no está. */
    fun match(deviceName: String?): Profile? =
        deviceName?.takeIf { it.isNotBlank() }?.let { n -> ALL.firstOrNull { it.pattern.containsMatchIn(n) } }
}
''' % "\n".join(rows))
print(len(rows), "perfiles")
