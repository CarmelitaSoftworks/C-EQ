#!/usr/bin/env python3
"""
Skins originales de C-EQ (diseñados en Figma: Carmelita, Cobre, Neón, Fósforo y Hielo) en el formato
clásico .wsz.

Para cada uno dibuja las imágenes en las posiciones estándar (MAIN.BMP, CBUTTONS.BMP, EQMAIN.BMP…),
las empaqueta en skins/<Nombre>.wsz y escribe su catálogo en OriginalSkins.kt. La ventana que usa la
app (assets/skins/ceq-<id>.png) la arma la propia app desde el .wsz, con OriginalSkinsTest:

    python3 tools/gen_original_skins.py
    ./gradlew testDebugUnitTest --tests cl.ecualizador.OriginalSkinsTest -DoriginalWindowsOut=src/main/assets/skins
"""
import io
import os
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Paletas de los diseños de Figma. «on»: letra de un botón encendido (si no, el acento); «osc»: tono
# final del analizador. Lo que falta se deriva en derive().
SKINS = [
    dict(id="carmelita", name="Carmelita",
         body="#2b2442", body2="#1b1530", light="#5a4a86", dark="#0d0a17",
         t1="#a77cff", t2="#3d2580", tt="#f3eaff", t1_off="#6d5a9a", t2_off="#2a2142",
         lcd="#0a0714", lt="#cfb0ff", ld="#2c2148", ac="#ff7ad9", osc="#7a52e0",
         btn="#3a3058", btn_p="#2a2242", btnL="#6d5ca3", btnD="#120e20", ic="#e6d9ff",
         knob="#e3d4ff", knob_p="#ffffff", track="#130f22"),
    dict(id="cobre", name="Cobre",
         body="#6b4426", body2="#3f2614", light="#c98a55", dark="#1e1108",
         t1="#d99a5b", t2="#7a4a22", tt="#2a1708",
         lcd="#160c03", lt="#ffb21a", ld="#4a2c08", ac="#ff5a1f", on="#6a1300", osc="#ff8a00",
         btn="#b8814f", btnL="#f0c08a", btnD="#4a2c14", ic="#2a1708",
         knob="#f2c58e", track="#2a1708", label="#f6d2a2"),
    dict(id="neon", name="Neón",
         body="#0f0e1a", body2="#08070f", light="#3b2f63", dark="#000000",
         t1="#ff2bd6", t2="#00d5ff", tt="#ffffff",
         lcd="#04030a", lt="#00f0ff", ld="#10303a", ac="#ff2bd6", osc="#00f0ff",
         btn="#1b1830", btnL="#5b3fa8", btnD="#05040a", ic="#00f0ff",
         knob="#ff2bd6", track="#05040a"),
    dict(id="fosforo", name="Fósforo",
         body="#38422f", body2="#252c1f", light="#6b7a58", dark="#12160e",
         t1="#56663f", t2="#2b331f", tt="#c8ff9a",
         lcd="#021006", lt="#3dff6e", ld="#0b3a17", ac="#d4ff3d", osc="#1f9e3a",
         btn="#4a573c", btnL="#7d8f66", btnD="#171c12", ic="#c8ff9a",
         knob="#a9b98f", track="#141a10"),
    dict(id="hielo", name="Hielo",
         body="#c7cfd9", body2="#9aa6b4", light="#ffffff", dark="#5f6b7a",
         t1="#5d9cff", t2="#d6e8ff", tt="#0b2142",
         lcd="#08182a", lt="#8fd8ff", ld="#163450", ac="#2f7bff", osc="#8fd8ff",
         btn="#dfe5ec", btnL="#ffffff", btnD="#77839a", ic="#1d3557",
         knob="#ffffff", track="#6b7788", label="#0b2142"),
]

P = {}


def derive(spec):
    p = dict(spec)
    p.setdefault("t1_off", mix(p["t1"], p["body"], 0.55))
    p.setdefault("t2_off", mix(p["t2"], p["body"], 0.55))
    p.setdefault("btn_p", mix(p["btn"], p["btnD"], 0.35))
    p.setdefault("knob_p", mix(p["knob"], "#ffffff", 0.5))
    p.setdefault("on", p["ac"])
    p.setdefault("label", p["tt"])
    return p


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return "#%02x%02x%02x" % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ------------------------------------------------------------------ dibujo

class Canvas:
    def __init__(self, w, h, bg="#000000"):
        self.im = Image.new("RGB", (w, h), rgb(bg))
        self.d = ImageDraw.Draw(self.im)

    def rect(self, x, y, w, h, c):
        if w > 0 and h > 0:
            self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(c))

    def px(self, x, y, c):
        self.d.point((x, y), fill=rgb(c))

    def vgrad(self, x, y, w, h, a, b):
        for i in range(h):
            self.rect(x, y + i, w, 1, mix(a, b, i / max(h - 1, 1)))

    def hgrad(self, x, y, w, h, a, b):
        for i in range(w):
            self.rect(x + i, y, 1, h, mix(a, b, i / max(w - 1, 1)))

    def bevel(self, x, y, w, h, base, inset=False, light=None, dark=None):
        light = light or P["btnL"]
        dark = dark or P["btnD"]
        self.rect(x, y, w, h, base)
        tl, br = (dark, light) if inset else (light, dark)
        self.rect(x, y, w, 1, tl)
        self.rect(x, y, 1, h, tl)
        self.rect(x, y + h - 1, w, 1, br)
        self.rect(x + w - 1, y, 1, h, br)

    def poly(self, pts, c):
        self.d.polygon(pts, fill=rgb(c))

    def text(self, s, x, y, c, bold=False):
        for i, ch in enumerate(s.upper()):
            g = FONT.get(ch)
            if g is None:
                x += 4
                continue
            for r, row in enumerate(g):
                for k, v in enumerate(row):
                    if v == "#":
                        self.px(x + k, y + r, c)
            x += len(g[0]) + (2 if bold else 1)
        return x

    def text_width(self, s, bold=False):
        return sum((len(FONT.get(ch, ["..."])[0]) + (2 if bold else 1)) for ch in s.upper()) - 1


# Letra de 3×5 (4×5 en M, N y W), propia
FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "J": ["..#", "..#", "..#", "#.#", ".#."],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#..#", "####", "####", "#..#", "#..#"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Q": [".#.", "#.#", "#.#", "##.", ".##"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#..#", "#..#", "####", "####", "#..#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "Z": ["###", "..#", ".#.", "#..", "###"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    '"': ["#.#", "#.#", "...", "...", "..."], "@": [".#.", "#.#", "###", "#..", ".##"],
    "…": ["...", "...", "...", "...", "#.#"], ".": [".", ".", ".", ".", "#"],
    ":": [".", "#", ".", "#", "."], "(": [".#", "#.", "#.", "#.", ".#"], ")": ["#.", ".#", ".#", ".#", "#."],
    "-": ["...", "...", "###", "...", "..."], "'": ["#", "#", ".", ".", "."], "!": ["#", "#", "#", ".", "#"],
    "_": ["...", "...", "...", "...", "###"], "+": ["...", ".#.", "###", ".#.", "..."],
    "\\": ["#..", "#..", ".#.", "..#", "..#"], "/": ["..#", "..#", ".#.", "#..", "#.."],
    "[": ["##", "#.", "#.", "#.", "##"], "]": ["##", ".#", ".#", ".#", "##"], "^": [".#.", "#.#", "...", "...", "..."],
    "&": [".#.", "#.#", ".#.", "#.#", ".##"], "%": ["#.#", "..#", ".#.", "#..", "#.#"], ",": ["..", "..", "..", ".#", "#."],
    "=": ["...", "###", "...", "###", "..."], "$": [".##", "##.", ".#.", ".##", "##."], "#": ["#.#", "###", "#.#", "###", "#.#"],
    "Å": [".#.", ".#.", "#.#", "###", "#.#"], "Ö": ["#.#", ".#.", "#.#", "#.#", ".#."], "Ä": ["#.#", ".#.", "#.#", "###", "#.#"],
    "?": ["##.", "..#", ".#.", "...", ".#."], "*": ["...", "#.#", ".#.", "#.#", "..."],
}


def frame(c, w, h):
    """Fondo de ventana: degradado vertical con borde claro arriba/izquierda y oscuro abajo/derecha."""
    c.vgrad(0, 0, w, h, P["body"], P["body2"])
    c.rect(0, 0, w, 1, P["light"]); c.rect(0, 0, 1, h, P["light"])
    c.rect(0, h - 1, w, 1, P["dark"]); c.rect(w - 1, 0, 1, h, P["dark"])


def title_strip(c, x, y, w, label, active=True, buttons=True):
    a, b = (P["t1"], P["t2"]) if active else (P["t1_off"], P["t2_off"])
    c.hgrad(x, y, w, 14, a, b)
    c.rect(x, y + 13, w, 1, P["dark"])
    grip = mix(P["tt"], a, 0.7)
    tw = c.text_width(label, bold=True)
    tx = x + (w - tw) // 2
    for i in range(4):
        c.rect(x + 6, y + 3 + i * 2, tx - x - 12, 1, grip)
        c.rect(tx + tw + 6, y + 3 + i * 2, x + w - 38 - (tx + tw + 6), 1, grip)
    c.text(label, tx, y + 4, P["tt"] if active else mix(P["tt"], a, 0.4), bold=True)
    if buttons:
        for i in range(3):
            bx = x + 244 + i * 10
            c.bevel(bx, y + 3, 9, 9, P["btn"])
        c.rect(x + 246, y + 9, 5, 1, P["ic"])                                      # minimizar
        c.rect(x + 256, y + 5, 5, 5, P["ic"]); c.rect(x + 257, y + 6, 3, 3, P["btn"])  # plegar
        for k in range(5):                                                            # cerrar
            c.px(x + 266 + k, y + 5 + k, P["ic"]); c.px(x + 270 - k, y + 5 + k, P["ic"])


# Iconos de los botones de transporte (9×8)
def icon(c, kind, x, y, col):
    if kind == "prev":
        c.rect(x, y, 2, 8, col); c.poly([(x + 9, y), (x + 9, y + 7), (x + 3, y + 3.5)], col)
    elif kind == "play":
        c.poly([(x + 1, y), (x + 1, y + 7), (x + 8, y + 3.5)], col)
    elif kind == "pause":
        c.rect(x + 1, y, 3, 8, col); c.rect(x + 5, y, 3, 8, col)
    elif kind == "stop":
        c.rect(x + 1, y, 7, 8, col)
    elif kind == "next":
        c.poly([(x, y), (x, y + 7), (x + 6, y + 3.5)], col); c.rect(x + 7, y, 2, 8, col)
    elif kind == "eject":
        c.poly([(x + 4, y), (x + 8, y + 4), (x, y + 4)], col); c.rect(x, y + 6, 9, 2, col)


# ------------------------------------------------------------------ imágenes del skin

def main_bmp():
    c = Canvas(275, 116)
    frame(c, 275, 116)
    # Pantalla: reloj, estado y visor (los dibuja la app)
    c.bevel(10, 20, 96, 44, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    c.rect(72, 30, 2, 2, P["lt"]); c.rect(72, 35, 2, 2, P["lt"])   # dos puntos del reloj
    c.rect(24, 60, 76, 1, P["ld"])                                   # base del visor
    # Título de la canción
    c.bevel(108, 23, 159, 14, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    # kbps y kHz
    c.bevel(109, 41, 18, 10, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    c.text("kbps", 130, 43, P["label"])
    c.bevel(154, 41, 14, 10, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    c.text("khz", 171, 43, P["label"])
    # Logo
    c.bevel(246, 88, 21, 19, P["body2"], inset=True, light=P["light"], dark=P["dark"])
    for (dx, dy, w, h) in [(5, 3, 10, 2), (3, 5, 2, 9), (5, 14, 10, 2), (15, 5, 2, 2), (15, 12, 2, 2)]:
        c.rect(246 + dx, 88 + dy, w, h, P["ac"])
    return c.im


def titlebar_bmp():
    c = Canvas(344, 87, P["body2"])
    title_strip(c, 27, 0, 275, "C-EQ", True)
    title_strip(c, 27, 15, 275, "C-EQ", False)
    # Ventana plegada (activa e inactiva)
    for row, active in ((29, True), (42, False)):
        a, b = (P["t1"], P["t2"]) if active else (P["t1_off"], P["t2_off"])
        c.hgrad(27, row, 275, 14, a, b)
        c.rect(27, row + 13, 275, 1, P["dark"])
        c.text("C-EQ", 27 + 6, row + 4, P["tt"], bold=True)
        c.bevel(27 + 77, row + 3, 42, 8, P["lcd"], inset=True, light=P["light"], dark=P["dark"])   # mini visor
        c.bevel(27 + 125, row + 3, 36, 8, P["lcd"], inset=True, light=P["light"], dark=P["dark"])  # reloj
        mini = [("prev", 169, 7), ("play", 176, 10), ("pause", 186, 9), ("stop", 195, 9), ("next", 204, 10), ("eject", 215, 9)]
        for kind, bx, bw in mini:
            c.bevel(27 + bx, row + 3, bw, 8, P["btn"])
        for kind, bx, bw in mini:
            cx = 27 + bx + bw // 2
            col = P["ic"]
            if kind == "play": c.poly([(cx - 1, row + 5), (cx - 1, row + 8), (cx + 2, row + 6.5)], col)
            elif kind == "pause": c.rect(cx - 2, row + 5, 1, 4, col); c.rect(cx, row + 5, 1, 4, col)
            elif kind == "stop": c.rect(cx - 2, row + 5, 4, 4, col)
            elif kind == "prev": c.rect(cx - 2, row + 5, 1, 4, col); c.poly([(cx + 1, row + 5), (cx + 1, row + 8), (cx - 1, row + 6.5)], col)
            elif kind == "next": c.poly([(cx - 2, row + 5), (cx - 2, row + 8), (cx, row + 6.5)], col); c.rect(cx + 1, row + 5, 1, 4, col)
            elif kind == "eject": c.poly([(cx, row + 5), (cx + 2, row + 7), (cx - 2, row + 7)], col); c.rect(cx - 2, row + 8, 5, 1, col)
        c.bevel(27 + 226, row + 4, 17, 7, P["track"], inset=True, light=P["light"], dark=P["dark"])  # posición
        for i in range(3):
            c.bevel(27 + 244 + i * 10, row + 3, 9, 9, P["btn"])
    # Barra de posición de la ventana plegada y su indicador
    c.bevel(0, 36, 17, 7, P["track"], inset=True, light=P["light"], dark=P["dark"])
    c.rect(20, 36, 3, 7, P["knob"])
    return c.im


def cbuttons_bmp():
    c = Canvas(136, 36, P["body2"])
    kinds = ["prev", "play", "pause", "stop", "next"]
    for row, pressed in ((0, False), (18, True)):
        for i, k in enumerate(kinds):
            x, w = (23 * i, 23) if i < 4 else (92, 22)
            c.bevel(x, row, w, 18, P["btn_p"] if pressed else P["btn"], inset=pressed)
            o = 1 if pressed else 0
            icon(c, k, x + (w - 9) // 2 + o, row + 5 + o, P["on"] if pressed else P["ic"])
    for row, pressed in ((0, False), (16, True)):
        c.bevel(114, row, 22, 16, P["btn_p"] if pressed else P["btn"], inset=pressed)
        o = 1 if pressed else 0
        icon(c, "eject", 114 + 6 + o, row + 4 + o, P["on"] if pressed else P["ic"])
    return c.im


def posbar_bmp():
    c = Canvas(307, 10, P["body2"])
    c.bevel(0, 0, 248, 10, P["track"], inset=True, light=P["light"], dark=P["dark"])
    c.rect(2, 4, 244, 2, P["ld"])
    for x, base in ((248, P["knob"]), (278, P["knob_p"])):
        c.bevel(x, 0, 29, 10, base, light="#ffffff", dark=P["btnL"])
        for k in range(3):
            c.rect(x + 11 + k * 3, 3, 1, 4, P["btnL"])
    return c.im


def slider_sheet(width, fill_mode, main):
    """VOLUME.BMP / BALANCE.BMP: 28 cuadros de 68×13 cada 15 px y el indicador abajo."""
    c = Canvas(68, 433, P["body2"])
    # Fondo igual al de MAIN.BMP donde va cada uno, para que no se note el recorte
    bg = main.crop((107, 57, 175, 70)) if fill_mode == "volume" else main.crop((177 - 9, 57, 177 - 9 + 68, 70))
    for f in range(28):
        y = f * 15
        c.im.paste(bg, (0, y))
        c.bevel(0, y + 3, 68, 7, P["track"], inset=True, light=P["light"], dark=P["dark"])
        t = f / 27
        if fill_mode == "volume":
            w = round(2 + t * 62)
            c.hgrad(1, y + 5, w, 3, P["lt"], mix(P["lt"], P["ac"], t))
        else:
            # Balance: se usa el tramo central (x 9..46); se ilumina hacia el lado elegido
            w = round(t * 18)
            c.rect(9 + 19 - w // 2, y + 5, max(w, 2), 3, mix(P["ld"], P["ac"], t))
    for x, base in ((15, P["knob"]), (0, P["knob_p"])):
        c.bevel(x, 422, 14, 11, base, light="#ffffff", dark=P["btnL"])
        c.rect(x + 4, 425, 1, 5, P["btnL"]); c.rect(x + 6, 425, 1, 5, P["btnL"]); c.rect(x + 8, 425, 1, 5, P["btnL"])
    return c.im


def shufrep_bmp():
    c = Canvas(92, 85, P["body2"])
    for i, (on, pressed) in enumerate(((False, False), (False, True), (True, False), (True, True))):
        y = i * 15
        for x, w, label in ((0, 28, "REP"), (28, 47, "SHUFFLE")):
            c.bevel(x, y, w, 15, P["btn_p"] if pressed else P["btn"], inset=pressed)
            led = P["ac"] if on else P["ld"]
            c.rect(x + 3, y + 5, 3, 4, led)
            c.text(label, x + 8 + (1 if pressed else 0), y + 5, P["on"] if on else P["ic"], bold=False)
    for col, (x0, label) in enumerate(((0, "EQ"), (23, "PL"))):
        for (dx, dy, on, pressed) in ((0, 61, False, False), (0, 73, True, False), (46, 61, False, True), (46, 73, True, True)):
            x = x0 + dx
            c.bevel(x, dy, 23, 12, P["btn_p"] if pressed else P["btn"], inset=pressed)
            c.rect(x + 3, dy + 4, 3, 4, P["ac"] if on else P["ld"])
            c.text(label, x + 9, dy + 4, P["on"] if on else P["ic"], bold=True)
    return c.im


def monoster_bmp(main):
    c = Canvas(58, 24, P["body2"])
    for y, on in ((0, True), (12, False)):
        c.im.paste(main.crop((239, 41, 268, 53)), (0, y))
        c.im.paste(main.crop((212, 41, 239, 53)), (29, y))
        c.text("STEREO", 3, y + 4, P["ac"] if on else P["ld"])
        c.text("MONO", 33, y + 4, P["ac"] if on else P["ld"])
    return c.im


def playpaus_bmp():
    c = Canvas(42, 9, P["lcd"])
    c.poly([(1, 1), (1, 7), (7, 4)], P["lt"])                       # reproduciendo
    c.rect(10, 1, 2, 7, P["lt"]); c.rect(14, 1, 2, 7, P["lt"])      # pausa
    c.rect(19, 1, 6, 6, P["lt"])                                    # detenido
    c.rect(36, 0, 3, 9, P["ac"]); c.rect(39, 0, 3, 9, P["ld"])
    return c.im


SEGMENTS = {
    "a": (1, 0, 7, 2), "b": (6, 0, 2, 7), "c": (6, 6, 2, 7), "d": (1, 11, 7, 2),
    "e": (1, 6, 2, 7), "f": (1, 0, 2, 7), "g": (1, 5, 7, 2),
}
DIGITS = ["abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"]


def numbers_bmp():
    c = Canvas(99, 13, P["lcd"])
    for n, segs in enumerate(DIGITS):
        x = n * 9
        for s, (sx, sy, w, h) in SEGMENTS.items():
            c.rect(x + sx, sy, w, h, P["lt"] if s in segs else P["ld"])
    for s, (sx, sy, w, h) in SEGMENTS.items():   # espacio en blanco (sin segmentos)
        c.rect(90 + sx, sy, w, h, P["ld"])
    return c.im


TEXT_ROWS = [
    list("ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@") + [" ", " ", " "],
    list("0123456789…" + ".:()-'!_+\\/[]^&%,=$#"),
    list("ÅÖÄ?*") + [" "] * 26,
]


def text_bmp():
    c = Canvas(155, 18, P["lcd"])
    for r, row in enumerate(TEXT_ROWS):
        for col, ch in enumerate(row[:31]):
            g = FONT.get(ch)
            if not g:
                continue
            ox = col * 5 + (1 if len(g[0]) <= 3 else 0)
            for y, line in enumerate(g):
                for x, v in enumerate(line):
                    if v == "#":
                        c.px(ox + x, r * 6 + y, P["lt"])
    return c.im


CURVE = [0.55, 0.45, 0.4, 0.48, 0.55, 0.6, 0.52, 0.4, 0.35, 0.3]
FREQS = ["60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K"]


def eqmain_bmp():
    c = Canvas(275, 315, P["body2"])
    frame(c, 275, 116)
    title_strip(c, 0, 0, 275, "EQUALIZER", True, buttons=False)
    # Escala y etiquetas
    c.text("+12", 46, 41, P["label"]); c.text("0", 52, 67, P["label"]); c.text("-12", 46, 94, P["label"])
    for x in (36, 64):
        for y in (43, 69, 96):
            c.rect(x, y, 4, 1, P["light"])
    c.rect(70, 38, 1, 63, P["dark"]); c.rect(71, 38, 1, 63, P["light"])
    c.text("PRE", 22, 104, P["label"])
    for i, f in enumerate(FREQS):
        x = 78 + i * 18 + 7 - c.text_width(f) // 2
        c.text(f, x, 104, P["label"])
    # Marco de la curva (el fondo lo pone la app)
    c.bevel(85, 16, 115, 21, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    # Título activo e inactivo
    title_strip(c, 0, 134, 275, "EQUALIZER", True)
    title_strip(c, 0, 149, 275, "EQUALIZER", False)
    # ON y AUTO: apagado, encendido, y sus versiones presionadas
    for x, w, label, on, pressed in (
        (10, 26, "ON", False, False), (69, 26, "ON", True, False), (128, 26, "ON", False, True), (187, 26, "ON", True, True),
        (36, 32, "AUTO", False, False), (95, 32, "AUTO", True, False), (154, 32, "AUTO", False, True), (213, 32, "AUTO", True, True),
    ):
        c.bevel(x, 119, w, 12, P["btn_p"] if pressed else P["btn"], inset=pressed)
        c.rect(x + 3, 123, 3, 4, P["ac"] if on else P["ld"])
        c.text(label, x + 9, 123, P["on"] if on else P["ic"], bold=True)
    # Deslizadores: 28 fondos de 14×63 (de −12 a +12 dB), 14 por fila, cada 15 px
    for i in range(28):
        x = 13 + (i % 14) * 15
        y = 164 + (i // 14) * 65
        c.bevel(x, y, 14, 63, P["track"], inset=True, light=P["light"], dark=P["dark"])
        t = i / 27
        top = round(4 + (1 - t) * 52)
        c.vgrad(x + 6, y + top, 2, 61 - top, mix(P["ac"], P["lt"], 1 - t), P["lt"])
    # Indicador normal y presionado
    for y, base in ((164, P["knob"]), (176, P["knob_p"])):
        c.bevel(0, y, 11, 11, base, light="#ffffff", dark=P["btnL"])
        c.rect(2, y + 5, 7, 1, P["btnL"])
    # PRESETS
    c.bevel(224, 164, 44, 12, P["btn"])
    c.text("PRESETS", 224 + (44 - c.text_width("PRESETS")) // 2, 168, P["ic"])
    c.bevel(224, 176, 44, 12, P["btn_p"], inset=True)
    c.text("PRESETS", 225 + (44 - c.text_width("PRESETS")) // 2, 169 + 12, P["on"])
    # Fondo de la curva, línea del preamplificador y colores de la curva por altura
    c.rect(0, 294, 113, 19, P["lcd"])
    c.rect(0, 303, 113, 1, P["ld"])
    for k in range(10):
        c.rect(4 + k * 12, 295, 1, 17, mix(P["lcd"], P["ld"], 0.6))
    c.rect(0, 314, 113, 1, P["ac"])
    for y in range(19):
        c.px(115, 294 + y, mix(P["ac"], P["lt"], y / 18))
    return c.im


def pledit_bmp():
    c = Canvas(280, 186, P["body2"])

    def top(x, w, title=None):
        c.vgrad(x, 0, w, 20, P["t1"], P["t2"])
        c.rect(x, 0, w, 1, P["light"]); c.rect(x, 19, w, 1, P["dark"])
        if title:
            c.text(title, x + (w - c.text_width(title)) // 2, 8, P["tt"])
        else:
            for i in range(4):
                c.rect(x, 6 + i * 2, w, 1, mix(P["tt"], P["t1"], 0.6))

    top(0, 25); top(26, 100, "PLAYLIST"); top(127, 25); top(153, 25)
    c.rect(0, 0, 1, 20, P["light"])
    for i in range(2):   # cerrar y plegar en la esquina derecha
        c.bevel(153 + 6 + i * 10, 5, 9, 9, P["btn"])
    # Lados
    c.vgrad(0, 42, 12, 29, P["body"], P["body"]); c.rect(0, 42, 1, 29, P["light"]); c.rect(11, 42, 1, 29, P["dark"])
    c.vgrad(31, 42, 20, 29, P["body"], P["body"]); c.rect(31 + 5, 42, 1, 29, P["dark"])
    c.rect(31 + 6, 42, 8, 29, P["track"]); c.rect(31 + 14, 42, 1, 29, P["light"]); c.rect(31 + 19, 42, 1, 29, P["dark"])
    c.bevel(52, 53, 8, 18, P["knob"], light="#ffffff", dark=P["btnL"])
    # Abajo a la izquierda: botones
    frame_part = Canvas(125, 38)
    frame_part.vgrad(0, 0, 125, 38, P["body"], P["body2"])
    frame_part.rect(0, 37, 125, 1, P["dark"]); frame_part.rect(0, 0, 1, 38, P["light"])
    for i, label in enumerate(("ADD", "REM", "SEL", "MISC")):
        frame_part.bevel(11 + i * 29, 12, 25, 18, P["btn"])
        frame_part.text(label, 11 + i * 29 + (25 - frame_part.text_width(label)) // 2, 19, P["ic"])
    c.im.paste(frame_part.im, (0, 72))
    # Relleno de abajo
    c.vgrad(179, 0, 25, 38, P["body"], P["body2"]); c.rect(179, 37, 25, 1, P["dark"])
    # Abajo a la derecha: opciones, mini controles y tiempo
    br = Canvas(150, 38)
    br.vgrad(0, 0, 150, 38, P["body"], P["body2"])
    br.rect(0, 37, 150, 1, P["dark"]); br.rect(149, 0, 1, 38, P["dark"])
    br.bevel(8, 6, 92, 9, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    for i, k in enumerate(("prev", "play", "pause", "stop", "next", "eject")):
        x = 8 + i * 9
        br.bevel(x, 21, 8, 9, P["btn"])
        br.rect(x + 3, 24, 2, 3, P["ic"])
    br.bevel(66, 21, 34, 9, P["lcd"], inset=True, light=P["light"], dark=P["dark"])
    br.bevel(104, 12, 25, 18, P["btn"])
    br.text("LIST", 108, 15, P["ic"]); br.text("OPTS", 108, 22, P["ic"])
    for k in range(5):
        br.rect(137 + k, 34 - k, 6 - k, 1, P["light"])
    c.im.paste(br.im, (126, 72))
    return c.im


def viscolor():
    return (
        [P["lcd"], mix(P["lcd"], P["ld"], 0.6) if P["id"] != "carmelita" else "#281e40"]
        + [mix(P["ac"], P["osc"], i / 15) for i in range(16)]
        + [mix("#ffffff", P["lt"], i / 4) for i in range(5)]
        + [P["knob"]]
    )


def viscolor_txt():
    lines = []
    for i, h in enumerate(viscolor()):
        r, g, b = rgb(h)
        lines.append(f"{r},{g},{b}, // color {i}")
    return "\n".join(lines) + "\n"


def pledit_txt():
    return f"""[Text]
Normal={P['lt']}
Current=#FFFFFF
NormalBG={P['lcd']}
SelectedBG={P['ld']}
Font=Arial
"""


def build(spec):
    P.clear()
    P.update(derive(spec))
    m = main_bmp()
    imgs = {
        "main.bmp": m, "titlebar.bmp": titlebar_bmp(), "cbuttons.bmp": cbuttons_bmp(),
        "posbar.bmp": posbar_bmp(), "volume.bmp": slider_sheet(68, "volume", m), "balance.bmp": slider_sheet(68, "balance", m),
        "shufrep.bmp": shufrep_bmp(), "monoster.bmp": monoster_bmp(m), "playpaus.bmp": playpaus_bmp(),
        "numbers.bmp": numbers_bmp(), "text.bmp": text_bmp(), "eqmain.bmp": eqmain_bmp(), "pledit.bmp": pledit_bmp(),
    }
    file_name = spec["name"].replace("ó", "o").replace("Ó", "O")
    wsz = os.path.join(ROOT, "skins", f"{file_name}.wsz")
    def entry(name):
        info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        return info

    with zipfile.ZipFile(wsz, "w", zipfile.ZIP_DEFLATED) as z:
        for name, im in imgs.items():
            buf = io.BytesIO()
            im.save(buf, "BMP")
            z.writestr(entry(name), buf.getvalue())
        z.writestr(entry("viscolor.txt"), viscolor_txt())
        z.writestr(entry("pledit.txt"), pledit_txt())
        z.writestr(entry("readme.txt"),
                   f"{spec['name']} - skin original de C-EQ (Carmelita Softworks). GPL-3.0.\n")

    def hexes(cols):
        return ", ".join("0x%02X%02X%02X" % rgb(h) for h in cols)
    chrome = [P["body"], P["light"], P["ac"], P["lt"], P["lcd"], P["lt"], "#ffffff", P["lcd"]]
    return f'''        WinampSkin(
            "{spec['name']}", "ceq-{spec['id']}",
            rgb({hexes(viscolor())}),
            rgb({hexes(chrome)}),
        ),'''


def main():
    os.makedirs(os.path.join(ROOT, "skins"), exist_ok=True)
    entries = "\n".join(build(spec) for spec in SKINS)
    kt = f'''package cl.ecualizador

/**
 * Skins originales de C-EQ, diseñados en Figma (generado por tools/gen_original_skins.py; no editar a
 * mano). Las ventanas están en assets/skins/ceq-*.png y los .wsz en la carpeta skins/ del repositorio.
 */
object OriginalSkins {{
    private fun rgb(vararg c: Int) = IntArray(c.size) {{ 0xFF000000.toInt() or c[it] }}

    val ALL: List<WinampSkin> = listOf(
{entries}
    )
}}
'''
    with open(os.path.join(ROOT, "app", "src", "main", "java", "cl", "ecualizador", "OriginalSkins.kt"), "w") as f:
        f.write(kt)
    print("listo:", ", ".join(s["name"] for s in SKINS))


if __name__ == "__main__":
    main()
