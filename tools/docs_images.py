#!/usr/bin/env python3
"""
Imágenes del README a partir de las capturas que generan las pruebas (con los skins originales de
C-EQ: Carmelita, Neón, Hielo…).

    ./gradlew testDebugUnitTest --tests 'cl.ecualizador.VivoV30PreviewTest' \\
        --tests 'cl.ecualizador.RendererPreviewTest' -DpreviewDir=<carpeta>
    python3 tools/docs_images.py <carpeta>
    tools/promo.sh <carpeta>   # el GIF (docs/promo.gif), aparte

Escribe en docs/: banner(-en,-pt).png, winamp-completo.png, barra-shade.png, fondo-animado.png,
modos-del-fondo.png, skins.png, patrones.png y tema-de-la-app.png.
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
BG = (20, 21, 27)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Paneles de v30_fondos.png: 16 de 420×933 cada 430 px (4 modos × 4 skins: el base y tres originales)
PANEL_W, PANEL_H, PANEL_STEP, MARGIN = 420, 933, 430, 10
MODES = ["player", "full", "shade", "spectrum"]
SKINS = ["base", "Carmelita", "Neón", "Hielo"]


def content_box(im, bg=None, tolerance=12):
    """Recuadro de lo que no es fondo (el color de la esquina)."""
    rgb = im.convert("RGB")
    bg = bg or rgb.getpixel((2, 2))
    diff = ImageChops.difference(rgb, Image.new("RGB", rgb.size, bg)).convert("L")
    return diff.point(lambda v: 255 if v > tolerance else 0).getbbox()


def panel(strip, mode, skin):
    i = MODES.index(mode) * len(SKINS) + SKINS.index(skin)
    x = MARGIN + i * PANEL_STEP
    return strip.crop((x, MARGIN, x + PANEL_W, MARGIN + PANEL_H))


def row(images, gap, margin, bg=BG):
    w = sum(im.width for im in images) + gap * (len(images) - 1) + 2 * margin
    h = max(im.height for im in images) + 2 * margin
    out = Image.new("RGB", (w, h), bg)
    x = margin
    for im in images:
        out.paste(im, (x, margin + (h - 2 * margin - im.height) // 2))
        x += im.width + gap
    return out


def scaled(im, height=None, width=None, nearest=False):
    if height:
        width = round(im.width * height / im.height)
    else:
        height = round(im.height * width / im.width)
    return im.resize((width, height), Image.NEAREST if nearest else Image.LANCZOS)


def full_skins(prev):
    """Las ventanas completas (reproductor, ecualizador y lista) de los skins originales."""
    out = []
    for name in ("Carmelita", "Ne_n", "Hielo"):
        im = Image.open(os.path.join(prev, f"v30_full_skin_{name}.png")).convert("RGB")
        out.append(im.crop(content_box(im)))
    return out


def banner(skins, lang):
    texts = {
        "es": ("Ecualizador para Android", "con skins clásicos", "10 bandas · espectro en vivo", "5 skins propios + 88 clásicos de Winamp 2"),
        "en": ("An Android equalizer", "with classic skins", "10 bands · live spectrum", "5 original skins + 88 classic Winamp 2 ones"),
        "pt": ("Equalizador para Android", "com skins clássicas", "10 bandas · espectro ao vivo", "5 skins próprias + 88 clássicas do Winamp 2"),
    }[lang]
    w, h = 1280, 640
    out = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(out)
    for y in range(h):   # degradado violeta oscuro
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=(round(28 - 14 * t), round(16 - 2 * t), round(52 - 34 * t)))
    # Las tres ventanas, una al lado de la otra y cortadas por el borde derecho
    x = 640
    for im in skins:
        s = scaled(im, height=560)
        out.paste(s, (x, 40))
        x += s.width - 10
    d.text((70, 168), "C-EQ", font=ImageFont.truetype(FONT_BOLD, 128), fill=(255, 255, 255))
    d.rectangle([74, 312, 214, 318], fill=(183, 136, 255))
    f = ImageFont.truetype(FONT, 32)
    d.text((72, 344), texts[0], font=f, fill=(236, 232, 255))
    d.text((72, 386), texts[1], font=f, fill=(236, 232, 255))
    f = ImageFont.truetype(FONT, 24)
    d.text((72, 456), texts[2], font=f, fill=(170, 165, 195))
    d.text((72, 490), texts[3], font=f, fill=(170, 165, 195))
    return out


def main():
    prev = sys.argv[1]
    skins = full_skins(prev)
    row([scaled(im, height=810) for im in skins], 20, 10).save(os.path.join(DOCS, "winamp-completo.png"), optimize=True)
    for lang, name in (("es", "banner.png"), ("en", "banner-en.png"), ("pt", "banner-pt.png")):
        banner(skins, lang).save(os.path.join(DOCS, name), optimize=True)

    strip = Image.open(os.path.join(prev, "v30_fondos.png")).convert("RGB")
    # Ventana plegada de Carmelita, a lo ancho
    shade = panel(strip, "shade", "Carmelita")
    shade = shade.crop(content_box(shade))
    scaled(shade, width=550).save(os.path.join(DOCS, "barra-shade.png"), optimize=True)
    # Fondo animado: el reproductor y el espectro de cada skin, recortados donde hay algo
    tiles = [panel(strip, m, s) for m in ("player", "spectrum") for s in SKINS]
    boxes = [content_box(t) for t in tiles]
    top = min(b[1] for b in boxes) - 20
    bottom = max(b[3] for b in boxes) + 20
    tiles = [scaled(t.crop((0, top, PANEL_W, bottom)), width=200) for t in tiles]
    strip_out = Image.new("RGB", (200 * 8, tiles[0].height), BG)
    for i, t in enumerate(tiles):
        strip_out.paste(t, (i * 200, 0))
    strip_out.save(os.path.join(DOCS, "fondo-animado.png"), optimize=True)
    # Modos del fondo: el reproductor y solo el espectro con Carmelita
    row([panel(strip, "player", "Carmelita"), panel(strip, "spectrum", "Carmelita")], 20, 10).save(
        os.path.join(DOCS, "modos-del-fondo.png"), optimize=True)

    sheet = Image.open(os.path.join(prev, "skins_winamp.png")).convert("RGB")
    sheet.crop((0, 0, sheet.width, min(sheet.height, 1560))).save(os.path.join(DOCS, "skins.png"), optimize=True)
    for src, dst in (("patrones_winamp.png", "patrones.png"), ("tema_app.png", "tema-de-la-app.png")):
        Image.open(os.path.join(prev, src)).convert("RGB").save(os.path.join(DOCS, dst), optimize=True)
    print("listo: docs/")


if __name__ == "__main__":
    main()
