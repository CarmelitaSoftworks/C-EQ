package cl.ecualizador

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Dibuja el analizador en un Canvas de Android. Lo usan el visor de la app (a ~60 fps) y el
 * widget de la pantalla de inicio (a pocos cuadros por segundo), para que se vean iguales.
 */
class SpectrumRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val grid = Paint().apply { color = 0x14FFFFFF; strokeWidth = 1f }
    private var shaderKey: Triple<SpectrumPalette, Float, Boolean>? = null
    private var shader: Shader? = null
    private val path = Path()
    private val topPath = Path()
    private var waveShaderKey: Pair<SpectrumPalette, Float>? = null
    private var waveShader: Shader? = null

    /** Degradado vertical de la paleta (el color depende de la altura). */
    private fun gradient(palette: SpectrumPalette, h: Float, mirror: Boolean): Shader {
        val key = Triple(palette, h, mirror)
        if (key == shaderKey) return shader!!
        val stops = palette.stops
        val colors = stops.map { it.second }.reversed().toIntArray()
        val positions = stops.map { 1f - it.first }.reversed().toFloatArray()
        val s = if (mirror) {
            // Del centro hacia las puntas, sin bajar del 35 % de la paleta para que no quede oscuro.
            LinearGradient(
                0f, 0f, 0f, h / 2,
                intArrayOf(palette.color(1f), palette.color(0.7f), palette.color(0.35f)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.MIRROR
            )
        } else {
            LinearGradient(0f, 0f, 0f, h, colors, positions, Shader.TileMode.CLAMP)
        }
        shaderKey = key; shader = s
        return s
    }

    /**
     * Dibuja el visor. [wave] es la forma de onda (−1..1) para los estilos de osciloscopio.
     */
    fun draw(
        canvas: Canvas, w: Float, h: Float, values: FloatArray, peaks: FloatArray?, s: SpectrumSettings,
        density: Float = 1f, wave: FloatArray? = null,
    ) {
        val n = values.size
        if (w <= 0 || h <= 0) return
        hitPart = null // lo vuelve a fijar el skin real si se dibuja
        stackScale = 0f
        // Cada estilo deja sus ajustes en los pinceles compartidos: se reinician en cada cuadro.
        fill.shader = null; fill.alpha = 255
        stroke.shader = null; stroke.alpha = 255; stroke.strokeCap = Paint.Cap.ROUND
        when (s.style) {
            SpectrumStyle.WINAMP_CLASSIC, SpectrumStyle.WINAMP_FIRE, SpectrumStyle.WINAMP_LINES, SpectrumStyle.WINAMP_SCOPE ->
                return drawWinamp(canvas, w, h, values, peaks.takeIf { s.peaks }, wave, s, density)
            SpectrumStyle.WAVE -> return drawWave(canvas, w, h, wave, s, density)
            SpectrumStyle.CIRCLE -> return drawCircle(canvas, w, h, values, s, density)
            else -> {}
        }
        if (n == 0) return
        if (s.grid) {
            listOf(0.25f, 0.5f, 0.75f).forEach { f -> canvas.drawLine(0f, h * f, w, h * f, grid) }
            listOf(50f, 100f, 250f, 1000f, 4000f, 10000f).forEach { hz ->
                val x = SpectrumScale.xOf(hz) * w
                canvas.drawLine(x, 0f, x, h, grid)
            }
        }
        val bw = w / n
        val gap = (bw * 0.18f).coerceAtLeast(if (n > 64) 0.5f else 1f)
        fun v(i: Int) = SpectrumShape.applySensitivity(values[i], s.sensitivity)
        fun p(i: Int) = peaks?.let { SpectrumShape.applySensitivity(it[i], s.sensitivity) } ?: 0f
        val capH = 1.5f * density
        val peakColor = 0xBFFFFFFF.toInt()

        when (s.style) {
            SpectrumStyle.BARS -> {
                fill.shader = gradient(s.palette, h, false)
                val radius = ((bw - gap) / 2).coerceAtMost(4f * density)
                for (i in 0 until n) {
                    val x = i * bw + gap / 2
                    val top = h - v(i) * h
                    if (v(i) > 0.002f) canvas.drawRoundRect(x, top, x + bw - gap, h + radius, radius, radius, fill)
                }
                fill.shader = null
                if (s.peaks && peaks != null) drawCaps(canvas, n, bw, gap, h, ::p, capH, peakColor)
            }
            SpectrumStyle.BLOCKS -> {
                val segments = (h / (5f * density)).toInt().coerceIn(8, 24)
                val segH = h / segments
                val segGap = (segH * 0.3f).coerceAtLeast(1f)
                for (i in 0 until n) {
                    val x = i * bw + gap / 2
                    val lit = Math.round(v(i) * segments)
                    for (k in 0 until segments) {
                        val c = s.palette.color((k + 1f) / segments)
                        // Los segmentos apagados se ven tenues, como en un ecualizador de equipo de música.
                        fill.color = if (k < lit) c else (c and 0x00FFFFFF) or 0x1A000000
                        val bottom = h - k * segH
                        canvas.drawRect(x, bottom - segH + segGap, x + bw - gap, bottom, fill)
                    }
                }
                if (s.peaks && peaks != null) drawCaps(canvas, n, bw, gap, h, ::p, (segH - segGap).coerceAtMost(2f * density), peakColor)
            }
            SpectrumStyle.MIRROR -> {
                fill.shader = gradient(s.palette, h, true)
                val mid = h / 2
                for (i in 0 until n) {
                    val x = i * bw + gap / 2
                    val half = v(i) * mid
                    if (half > 0.5f) canvas.drawRect(x, mid - half, x + bw - gap, mid + half, fill)
                }
                fill.shader = null
                if (s.peaks && peaks != null) {
                    fill.color = peakColor
                    for (i in 0 until n) {
                        val half = p(i) * mid
                        if (half < 1f) continue
                        val x = i * bw + gap / 2
                        canvas.drawRect(x, mid - half - capH, x + bw - gap, mid - half, fill)
                        canvas.drawRect(x, mid + half, x + bw - gap, mid + half + capH, fill)
                    }
                }
            }
            SpectrumStyle.LINE -> {
                path.reset()
                path.moveTo(0f, h)
                var prevX = 0f
                var prevY = h - v(0) * h
                path.lineTo(prevX, prevY)
                for (i in 1 until n) {
                    val x = i * bw + bw / 2
                    val y = h - v(i) * h
                    path.quadTo(prevX, prevY, (prevX + x) / 2, (prevY + y) / 2)
                    prevX = x; prevY = y
                }
                path.lineTo(w, prevY)
                path.lineTo(w, h)
                path.close()
                fill.shader = gradient(s.palette, h, false)
                fill.alpha = 110
                canvas.drawPath(path, fill)
                fill.alpha = 255
                fill.shader = null
                stroke.shader = gradient(s.palette, h, false)
                stroke.strokeWidth = 2.5f * density
                // Solo el borde superior: se vuelve a trazar sin los lados ni la base.
                val top = topPath.apply {
                    reset()
                    moveTo(bw / 2, h - v(0) * h)
                    var px = bw / 2; var py = h - v(0) * h
                    for (i in 1 until n) {
                        val x = i * bw + bw / 2; val y = h - v(i) * h
                        quadTo(px, py, (px + x) / 2, (py + y) / 2); px = x; py = y
                    }
                }
                canvas.drawPath(top, stroke)
                stroke.shader = null
                if (s.peaks && peaks != null) {
                    fill.color = (peakColor and 0x00FFFFFF) or 0x99000000.toInt()
                    for (i in 0 until n) {
                        val y = h - p(i) * h
                        if (p(i) > 0.01f) canvas.drawCircle(i * bw + bw / 2, y, 1.6f * density, fill)
                    }
                }
            }
            SpectrumStyle.DOTS -> {
                val r = ((bw - gap) / 2).coerceIn(1.5f * density, 6f * density)
                for (i in 0 until n) {
                    val cx = i * bw + bw / 2
                    val level = v(i)
                    // Columna de puntos tenues y un punto brillante en la cima.
                    fill.color = (s.palette.color(level) and 0x00FFFFFF) or 0x40000000
                    var y = h - r
                    while (y > h - level * h) { canvas.drawCircle(cx, y, r * 0.6f, fill); y -= r * 2.6f }
                    fill.color = s.palette.color(level)
                    canvas.drawCircle(cx, h - level * h, r, fill)
                }
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------- Winamp

    private var pixels: IntArray = IntArray(0)
    private var pixelBitmap: android.graphics.Bitmap? = null
    // Desde Android 9 Paint() suaviza al ampliar por defecto: se desactiva para conservar los píxeles.
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = android.graphics.RectF()

    private fun pixelCanvas(pw: Int, vis: IntArray, background: Int?): IntArray {
        val ph = WinampColors.HEIGHT
        if (pixelBitmap?.width != pw) {
            pixelBitmap = android.graphics.Bitmap.createBitmap(pw, ph, android.graphics.Bitmap.Config.ARGB_8888)
            pixels = IntArray(pw * ph)
        }
        // Fondo del skin con la grilla de puntos del Winamp clásico (o el fondo liso de la lista).
        for (y in 0 until ph) for (x in 0 until pw) {
            pixels[y * pw + x] = background ?: if (y % 2 == 1 && x % 2 == 1) vis[1] else vis[0]
        }
        return pixels
    }

    private fun blit(canvas: Canvas, w: Float, h: Float, pw: Int) {
        val bmp = pixelBitmap ?: return
        bmp.setPixels(pixels, 0, pw, 0, 0, pw, WinampColors.HEIGHT)
        dst.set(0f, 0f, w, h)
        canvas.drawBitmap(bmp, null, dst, pixelPaint)
    }

    private fun drawWinamp(
        canvas: Canvas, w: Float, h: Float, values: FloatArray, peaks: FloatArray?, wave: FloatArray?,
        s: SpectrumSettings, density: Float,
    ) {
        val skin = WinampSkins.byName(s.winampSkin)
        val window = if (s.winampFrame && s.winampWindow) WinampWindows.bitmap(skin) else null
        val area = when {
            window != null && s.winampStack -> drawSkinStack(canvas, w, h, window, skin, s.winampMarquee)
            window != null -> drawSkinWindow(canvas, w, h, window, s.winampPart, skin, s.winampMarquee)
            s.winampFrame -> drawWinampFrame(canvas, w, h, skin, density)
            else -> null
        }
        if (area != null) {
            canvas.save()
            canvas.translate(area.left, area.top)
        }
        val aw = area?.width() ?: w
        val ah = area?.height() ?: h
        if (s.style == SpectrumStyle.WINAMP_SCOPE) drawWinampScope(canvas, aw, ah, wave, skin, s.winampPattern)
        else drawWinampAnalyzer(canvas, aw, ah, values, peaks, s.style, skin, s.winampPattern)
        if (area != null) canvas.restore()
    }

    /** Analizador de Winamp 2: 19 bandas de 3 px (o 75 líneas de 1 px) en 16 px de alto. */
    private fun drawWinampAnalyzer(
        canvas: Canvas, w: Float, h: Float, values: FloatArray, peaks: FloatArray?, style: SpectrumStyle,
        skin: WinampSkin, pattern: WinampPattern,
    ) {
        val vis = skin.colors
        val thin = style == SpectrumStyle.WINAMP_LINES
        val fire = style == SpectrumStyle.WINAMP_FIRE
        val bands = values.size.coerceAtLeast(1)
        val bandW = if (thin) 1 else 3
        val step = bandW + if (thin) 0 else 1
        val pw = bands * step
        val ph = WinampColors.HEIGHT
        val px = pixelCanvas(pw, vis, pattern.background(skin.chrome))
        val peakColor = pattern.peak(vis, skin.chrome)
        for (b in 0 until bands) {
            val barH = Math.round(values[b].coerceIn(0f, 1f) * ph)
            for (row in 0 until barH) {
                val y = ph - 1 - row
                for (dx in 0 until bandW) {
                    val x = b * step + dx
                    val color = pattern.pixel(vis, skin.chrome, b, bands, row, barH, fire, x)
                    if (color != 0) px[y * pw + x] = color
                }
            }
            peaks?.let {
                val pk = Math.round(it[b].coerceIn(0f, 1f) * ph)
                if (pk > 0 && pk > barH) {
                    val y = ph - pk
                    for (dx in 0 until bandW) px[y * pw + b * step + dx] = peakColor
                }
            }
        }
        blit(canvas, w, h, pw)
    }

    /** Osciloscopio de Winamp 2: 75 puntos unidos verticalmente, más tenues lejos del centro. */
    private fun drawWinampScope(canvas: Canvas, w: Float, h: Float, wave: FloatArray?, skin: WinampSkin, pattern: WinampPattern) {
        val vis = skin.colors
        val pw = 75
        val ph = WinampColors.HEIGHT
        val px = pixelCanvas(pw, vis, pattern.background(skin.chrome))
        val mid = ph / 2
        var prevY = mid
        for (x in 0 until pw) {
            val v = wave?.let { it[(x * it.size / pw).coerceIn(0, it.size - 1)] } ?: 0f
            val y = (mid - Math.round(v.coerceIn(-1f, 1f) * (mid - 1))).coerceIn(0, ph - 1)
            val (a, b) = if (x == 0) y to y else minOf(prevY, y) to maxOf(prevY, y)
            for (yy in a..b) {
                val d = kotlin.math.abs(yy - mid)
                px[yy * pw + x] = when (pattern) {
                    WinampPattern.PLAYLIST -> if (yy == y) skin.chrome[WinampChrome.LIST_CURRENT] else skin.chrome[WinampChrome.LIST_TEXT]
                    WinampPattern.CHROME -> WinampColors.mix(skin.chrome[WinampChrome.ACCENT], skin.chrome[WinampChrome.FACE], d / mid.toFloat() * 0.6f)
                    WinampPattern.BANDS -> vis[(17 - x * 16 / pw).coerceIn(2, 17)]
                    else -> vis[WinampColors.scopeColorIndex(d)]
                }
            }
            prevY = y
        }
        blit(canvas, w, h, pw)
    }

    private val windowSrc = android.graphics.Rect()
    private val windowDst = android.graphics.RectF()

    /**
     * Skin real (la ventana entera o solo su pantalla, según [part]) lo más grande que quepa,
     * centrado; con factor entero si cabe al menos al doble, para que cada píxel del skin quede
     * como un bloque parejo y nítido. Encima, lo que suena en vivo. Devuelve el recuadro del visor.
     */
    private fun drawSkinWindow(
        canvas: Canvas, w: Float, h: Float, atlas: android.graphics.Bitmap, part: WinampWindow.Part, skin: WinampSkin,
        marquee: Boolean = true,
    ): android.graphics.RectF {
        val fit = minOf(w / part.w, h / part.h)
        val scale = if (fit >= 2f) kotlin.math.floor(fit) else fit
        val left = kotlin.math.round((w - part.w * scale) / 2)
        val top = kotlin.math.round((h - part.h * scale) / 2)

        /** Copia un recorte del atlas a la posición ([wx], [wy]) de la ventana del skin. */
        fun blit(sx: Int, sy: Int, sw: Int, sh: Int, wx: Float, wy: Float) {
            windowSrc.set(sx, sy, sx + sw, sy + sh)
            val x = left + (wx - part.x) * scale
            val y = top + (wy - part.y) * scale
            windowDst.set(x, y, x + sw * scale, y + sh * scale)
            canvas.drawBitmap(atlas, windowSrc, windowDst, pixelPaint)
        }

        hitPart = part; hitLeft = left; hitTop = top; hitScale = scale

        canvas.save()
        canvas.clipRect(left, top, left + part.w * scale, top + part.h * scale)
        blit(part.x, part.y, part.w, part.h, part.x.toFloat(), part.y.toFloat())
        // Las ventanas importadas antes de la 2.8 no traen las piezas: quedan como estaban
        if (atlas.height >= WinampWindow.LIVE_HEIGHT) {
            drawNowPlaying(skin, marquee, ::blit)
            // Botón recién tocado: la imagen «presionado» del skin por un instante
            val b = pressed
            if (b != null && android.os.SystemClock.elapsedRealtime() < pressedUntil) {
                blit(b.sx, WinampWindow.PRESSED_Y, b.w, b.h, b.x.toFloat(), b.y.toFloat())
            }
        }
        canvas.restore()
        frameArea.set(
            left + (WinampWindow.VIS_X - part.x) * scale, top + (WinampWindow.VIS_Y - part.y) * scale,
            left + (WinampWindow.VIS_X - part.x + WinampWindow.VIS_W) * scale,
            top + (WinampWindow.VIS_Y - part.y + WinampWindow.VIS_H) * scale,
        )
        return frameArea
    }

    // Dónde quedó el skin en el último cuadro, para saber qué se toca
    private var hitPart: WinampWindow.Part? = null
    private var hitLeft = 0f
    private var hitTop = 0f
    private var hitScale = 1f
    private var pressed: WinampWindow.Button? = null
    private var pressedUntil = 0L

    /**
     * Qué hay del skin en ([x], [y]) (en las coordenadas en que se dibujó, como en [draw]), o null
     * si no hay skin real o no se tocó nada.
     */
    fun hitAt(x: Float, y: Float): WinampWindow.Hit? {
        if (stackScale > 0f) return stackHit((x - stackLeft) / stackScale, (y - stackTop) / stackScale)
        val part = hitPart ?: return null
        return WinampWindow.hit(part.x + (x - hitLeft) / hitScale, part.y + (y - hitTop) / hitScale, part)
    }

    /** Muestra [button] presionado un instante. */
    fun press(button: WinampWindow.Button) {
        pressed = button
        pressedUntil = android.os.SystemClock.elapsedRealtime() + 180
    }

    // ---------------------------------------------------------------- Skin completo

    // Dónde quedó el skin completo en el último cuadro (escala 0: no se dibujó)
    private var stackScale = 0f
    private var stackLeft = 0f
    private var stackTop = 0f
    private var stackSides = 1
    private var playlistFirst = 0
    private var playlistShown = 0

    private fun stackHit(x: Float, y: Float): WinampWindow.Hit? {
        if (x < 0 || x >= WinampWindow.WIDTH || y < 0) return null
        val h = WinampWindow.HEIGHT
        if (y < h) return WinampWindow.hit(x, y, WinampWindow.Part.FULL)
        if (y < 2 * h) return WinampWindow.eqHit(x, y - h)
        val py = y - 2 * h - WinampWindow.PL_TOP_H - 2
        if (x < WinampWindow.PL_LEFT_W || x >= WinampWindow.WIDTH - WinampWindow.PL_RIGHT_W || py < 0) return null
        if (py >= WinampWindow.PL_SIDE_H * stackSides) return null
        val row = (py / WinampWindow.PL_ROW_H).toInt()
        return if (row < playlistShown) WinampWindow.Hit.PlaylistRow(playlistFirst + row) else null
    }

    /**
     * Skin completo hacia abajo: el reproductor (como [drawSkinWindow]), la ventana del ecualizador
     * con las bandas que suenan y la lista de reproducción con la cola. Devuelve el recuadro del visor.
     */
    private fun drawSkinStack(
        canvas: Canvas, w: Float, h: Float, atlas: android.graphics.Bitmap, skin: WinampSkin, marquee: Boolean,
    ): android.graphics.RectF {
        val scale = SkinStack.scale(w, h)
        val sides = SkinStack.sides(h, scale)
        val total = SkinStack.FIXED + WinampWindow.playlistHeight(sides)
        val left = kotlin.math.round((w - WinampWindow.WIDTH * scale) / 2)
        val top = kotlin.math.round((h - total * scale) / 2)
        val ww = WinampWindow.WIDTH * scale
        val wh = WinampWindow.HEIGHT * scale

        canvas.save()
        canvas.translate(left, top)
        val vis = drawSkinWindow(canvas, ww, wh, atlas, WinampWindow.Part.FULL, skin, marquee)
        canvas.restore()
        vis.offset(left, top)

        val (eqAtlas, plAtlas) = WinampWindows.extras(skin)
        eqAtlas?.let { drawEqualizer(canvas, it, left, top + wh, scale) }
        plAtlas?.let { drawPlaylist(canvas, it, left, top + 2 * wh, scale, sides, skin) }

        stackScale = scale; stackLeft = left; stackTop = top; stackSides = sides
        return vis
    }

    /** Copia un recorte del atlas a ([wx], [wy]) en píxeles del skin, desde ([x0], [y0]) en pantalla. */
    private fun put(canvas: Canvas, a: android.graphics.Bitmap, sx: Int, sy: Int, sw: Int, sh: Int, x0: Float, y0: Float, scale: Float, wx: Float, wy: Float) {
        windowSrc.set(sx, sy, sx + sw, sy + sh)
        val x = x0 + wx * scale
        val y = y0 + wy * scale
        windowDst.set(x, y, x + sw * scale, y + sh * scale)
        canvas.drawBitmap(a, windowSrc, windowDst, pixelPaint)
    }

    private var graphBitmap: android.graphics.Bitmap? = null
    private var graphKey: Any? = null

    /** Ventana del ecualizador: ON, AUTO, los 10 deslizadores y el preamplificador, y la curva. */
    private fun drawEqualizer(canvas: Canvas, a: android.graphics.Bitmap, x0: Float, y0: Float, scale: Float) {
        val eq = EqEngine.peek()?.state?.value
        fun p(sx: Int, sy: Int, sw: Int, sh: Int, wx: Float, wy: Float) = put(canvas, a, sx, sy, sw, sh, x0, y0, scale, wx, wy)
        p(0, WinampWindow.EQ_Y, WinampWindow.WIDTH, WinampWindow.HEIGHT, 0f, 0f)
        val on = eq?.enabled == true
        if (on) p(WinampWindow.EQ_THUMB_X + 28, WinampWindow.EQ_ON_Y, 26, 12, 14f, 18f)
        else p(WinampWindow.EQ_THUMB_X, WinampWindow.EQ_ON_Y, 26, 12, 14f, 18f)
        p(WinampWindow.EQ_THUMB_X, if (eq?.auto == true) WinampWindow.EQ_AUTO_ON_Y else WinampWindow.EQ_AUTO_Y, 32, 12, 40f, 18f)

        val levels = eq?.shownLevels ?: List(EqCore.BANDS) { 0f }
        fun fraction(db: Float) = ((db - EqCore.MIN_DB) / (EqCore.MAX_DB - EqCore.MIN_DB)).coerceIn(0f, 1f)
        fun slider(x: Int, f: Float) {
            val (fx, fy) = WinampWindow.eqSliderFrame(f)
            p(fx, fy, 14, WinampWindow.EQ_SLIDER_H, x.toFloat(), WinampWindow.EQ_SLIDER_Y.toFloat())
            val ty = WinampWindow.EQ_SLIDER_Y + Math.round((1f - f) * (WinampWindow.EQ_SLIDER_H - WinampWindow.EQ_THUMB))
            p(WinampWindow.EQ_THUMB_X, WinampWindow.EQ_SLIDERS_Y, WinampWindow.EQ_THUMB, WinampWindow.EQ_THUMB, x + 1f, ty.toFloat())
        }
        // Preamplificador: el volumen extra, de la mitad (0 dB) hacia arriba
        slider(WinampWindow.EQ_PREAMP_X, 0.5f + (eq?.boost ?: 0f) / EqCore.BOOST_MAX_DB / 2f)
        for (i in 0 until EqCore.BANDS) slider(WinampWindow.EQ_BAND_X + i * WinampWindow.EQ_BAND_STEP, fraction(levels.getOrElse(i) { 0f }))

        // Curva: se rearma solo si cambian el skin o las bandas
        val key = listOf(System.identityHashCode(a), levels)
        if (key != graphKey || graphBitmap == null) {
            graphKey = key
            val gw = WinampWindow.EQ_GRAPH_W
            val gh = WinampWindow.EQ_GRAPH_H
            val px = IntArray(gw * gh)
            a.getPixels(px, 0, gw, WinampWindow.EQ_GRAPH_X, WinampWindow.EQ_Y + WinampWindow.EQ_GRAPH_Y, gw, gh)
            val colors = IntArray(gh) { a.getPixel(WinampWindow.EQ_LINE_X, WinampWindow.EQ_SLIDERS_Y + it) }
            var prev = -1
            for (x in 0 until gw) {
                val pos = x / (gw - 1f) * (EqCore.BANDS - 1)
                val i = pos.toInt().coerceAtMost(EqCore.BANDS - 2)
                val db = levels.getOrElse(i) { 0f } + (levels.getOrElse(i + 1) { 0f } - levels.getOrElse(i) { 0f }) * (pos - i)
                val y = Math.round((1f - fraction(db)) * (gh - 1)).coerceIn(0, gh - 1)
                val (from, to) = if (prev < 0) y to y else minOf(prev, y) to maxOf(prev, y)
                for (yy in from..to) px[yy * gw + x] = colors[yy] or (0xFF shl 24)
                prev = y
            }
            val bmp = graphBitmap ?: android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888).also { graphBitmap = it }
            bmp.setPixels(px, 0, gw, 0, 0, gw, gh)
        }
        graphBitmap?.let { g ->
            windowSrc.set(0, 0, g.width, g.height)
            val x = x0 + WinampWindow.EQ_GRAPH_X * scale
            val y = y0 + WinampWindow.EQ_GRAPH_Y * scale
            windowDst.set(x, y, x + g.width * scale, y + g.height * scale)
            canvas.drawBitmap(g, windowSrc, windowDst, pixelPaint)
        }
    }

    private val listFill = Paint().apply { isAntiAlias = false }
    private val listText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.SANS_SERIF }

    /** Lista de reproducción: el marco del skin del alto pedido y la cola, con la que suena resaltada. */
    private fun drawPlaylist(canvas: Canvas, a: android.graphics.Bitmap, x0: Float, y0: Float, scale: Float, sides: Int, skin: WinampSkin) {
        fun p(sx: Int, sy: Int, sw: Int, sh: Int, wx: Float, wy: Float) = put(canvas, a, sx, sy, sw, sh, x0, y0, scale, wx, wy)
        val W = WinampWindow.WIDTH
        val topH = WinampWindow.PL_TOP_H
        val bodyH = WinampWindow.PL_SIDE_H * sides
        // Arriba: relleno, título al centro y esquinas
        var x = 0
        while (x < W) { p(WinampWindow.PL_TOP_TILE_X, WinampWindow.PL_Y, 25, topH, x.toFloat(), 0f); x += 25 }
        p(WinampWindow.PL_TITLE_X, WinampWindow.PL_Y, 100, topH, ((W - 100) / 2).toFloat(), 0f)
        p(WinampWindow.PL_TOP_LEFT_X, WinampWindow.PL_Y, 25, topH, 0f, 0f)
        p(WinampWindow.PL_TOP_RIGHT_X, WinampWindow.PL_Y, 25, topH, (W - 25).toFloat(), 0f)
        // Lados
        for (k in 0 until sides) {
            val y = (topH + k * WinampWindow.PL_SIDE_H).toFloat()
            p(WinampWindow.PL_LEFT_X, WinampWindow.PL_Y, WinampWindow.PL_LEFT_W, WinampWindow.PL_SIDE_H, 0f, y)
            p(WinampWindow.PL_RIGHT_X, WinampWindow.PL_Y, WinampWindow.PL_RIGHT_W, WinampWindow.PL_SIDE_H, (W - WinampWindow.PL_RIGHT_W).toFloat(), y)
        }
        // Abajo
        p(0, WinampWindow.PL_BOTTOM_Y, 125, WinampWindow.PL_BOTTOM_H, 0f, (topH + bodyH).toFloat())
        p(0, WinampWindow.PL_BOTTOM_RIGHT_Y, 150, WinampWindow.PL_BOTTOM_H, 125f, (topH + bodyH).toFloat())

        // Fondo de la lista con su color (PLEDIT.TXT)
        val textL = x0 + WinampWindow.PL_LEFT_W * scale
        val textR = x0 + (W - WinampWindow.PL_RIGHT_W) * scale
        val textT = y0 + topH * scale
        listFill.color = skin.chrome[WinampChrome.LIST_BG] or (0xFF shl 24)
        canvas.drawRect(textL, textT, textR, textT + bodyH * scale, listFill)

        val list = NowPlaying.playlist()
        val active = NowPlaying.activeIndex(list)
        val rows = WinampWindow.playlistRows(sides)
        val first = (active - 1).coerceAtMost(list.size - rows).coerceAtLeast(0)
        val shown = minOf(rows, list.size - first).coerceAtLeast(0)
        playlistFirst = first
        playlistShown = shown

        // Barra de desplazamiento según dónde va la lista
        val handleRoom = bodyH - 18
        val progress = if (list.size > rows) first / (list.size - rows).toFloat() else 0f
        p(WinampWindow.PL_HANDLE_X, WinampWindow.PL_Y, 8, 18, (W - 15).toFloat(), topH + Math.round(progress * handleRoom).toFloat())

        if (shown == 0) return
        listText.textSize = 9f * scale
        val track = NowPlaying.track.value
        canvas.save()
        canvas.clipRect(textL, textT, textR, textT + bodyH * scale)
        for (r in 0 until shown) {
            val i = first + r
            val e = list[i]
            val baseline = textT + (2 + r * WinampWindow.PL_ROW_H + 10) * scale
            listText.color = skin.chrome[if (i == active) WinampChrome.LIST_CURRENT else WinampChrome.LIST_TEXT] or (0xFF shl 24)
            // La duración solo se sabe de la que suena
            val time = if (i == active && track != null && track.durationMs > 0) PlayingTrack.clock(track.durationMs) else ""
            val timeW = if (time.isEmpty()) 0f else listText.measureText(time) + 4 * scale
            val name = "${i + 1}. " + if (e.artist.isBlank()) e.title else "${e.artist} - ${e.title}"
            val room = textR - textL - 4 * scale - timeW
            val n = listText.breakText(name, true, room, null)
            canvas.drawText(if (n < name.length) name.take((n - 1).coerceAtLeast(0)) + "…" else name, textL + 2 * scale, baseline, listText)
            if (time.isNotEmpty()) {
                listText.textAlign = Paint.Align.RIGHT
                canvas.drawText(time, textR - 2 * scale, baseline, listText)
                listText.textAlign = Paint.Align.LEFT
            }
        }
        canvas.restore()
    }

    private var titleSource = ""
    private var titleGlyphs = IntArray(0)

    /**
     * Lo que suena, con las piezas del propio skin: reproducir/pausa, reloj, indicador de posición y
     * el título que se desplaza como en Winamp. Sin acceso a lo que suena, el reloj queda en 00:00
     * y el título es el nombre del skin.
     */
    private fun drawNowPlaying(skin: WinampSkin, marquee: Boolean, blit: (Int, Int, Int, Int, Float, Float) -> Unit) {
        val track = NowPlaying.track.value
        val now = android.os.SystemClock.elapsedRealtime()
        val state = track?.state ?: PlayingTrack.State.PLAYING
        blit(state.ordinal * 9, WinampWindow.PLAYPAUS_Y, 9, 9, WinampWindow.PLAYPAUS_X.toFloat(), WinampWindow.PLAYPAUS_WY.toFloat())

        val pos = track?.positionAt(now) ?: 0L
        val secs = (pos / 1000).coerceAtMost(99 * 60 + 59)
        val digits = intArrayOf((secs / 60 / 10).toInt(), (secs / 60 % 10).toInt(), (secs % 60 / 10).toInt(), (secs % 60 % 10).toInt())
        for (k in 0 until 4) blit(digits[k] * 9, WinampWindow.NUMS_Y, 9, 13, WinampWindow.CLOCK_X[k].toFloat(), WinampWindow.CLOCK_Y.toFloat())

        // Volumen de la música: el cuadro de fondo que corresponde y el indicador
        val vol = NowPlaying.volume
        if (vol >= 0f) {
            val f = Math.round(vol.coerceIn(0f, 1f) * (WinampWindow.VOLUME_FRAMES - 1))
            blit((f % 4) * WinampWindow.VOLUME_W, WinampWindow.VOLUME_Y + (f / 4) * 13, WinampWindow.VOLUME_W, 13,
                WinampWindow.VOLUME_X.toFloat(), WinampWindow.VOLUME_WY.toFloat())
            val tx = WinampWindow.VOLUME_X + (WinampWindow.VOLUME_W - WinampWindow.VOLUME_THUMB_W) * vol.coerceIn(0f, 1f)
            blit(0, WinampWindow.VOLUME_THUMB_Y, WinampWindow.VOLUME_THUMB_W, 11, kotlin.math.floor(tx), WinampWindow.VOLUME_WY + 1f)
        }

        val progress = if (track != null && track.durationMs > 0) pos / track.durationMs.toFloat() else 0f
        val thumbX = WinampWindow.POSBAR_X + (WinampWindow.POSBAR_W - WinampWindow.THUMB_W) * progress.coerceIn(0f, 1f)
        blit(0, WinampWindow.THUMB_Y, WinampWindow.THUMB_W, 10, kotlin.math.floor(thumbX), WinampWindow.POSBAR_WY.toFloat())

        // Título: «1. Artista - Título (3:45)»; si no cabe, se desplaza 1 píxel del skin cada 40 ms
        val text = "1. " + (track?.winampTitle() ?: skin.name)
        if (text != titleSource) {
            titleSource = text
            val plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            titleGlyphs = IntArray(plain.length) { i -> WinampWindow.glyph(plain[i]).let { (c, r) -> c or (r shl 8) } }
        }
        val glyphs = if (marquee && titleGlyphs.size * 5 > WinampWindow.TITLE_W) titleGlyphs + MARQUEE_GAP else titleGlyphs
        val total = glyphs.size * 5
        val offset = if (glyphs === titleGlyphs) 0 else ((now / 40) % total).toInt()
        val x0 = WinampWindow.TITLE_X
        val x1 = x0 + WinampWindow.TITLE_W
        for (copy in 0..1) {
            for (i in glyphs.indices) {
                val x = x0 - offset + copy * total + i * 5
                if (x + 5 <= x0 || x >= x1) continue
                // Recorte del glifo a la zona del título
                val from = maxOf(0, x0 - x)
                val to = minOf(5, x1 - x)
                val g = glyphs[i]
                blit((g and 0xFF) * 5 + from, WinampWindow.TEXT_Y + (g shr 8) * 6, to - from, 6, (x + from).toFloat(), WinampWindow.TITLE_Y.toFloat())
            }
            if (glyphs === titleGlyphs) break
        }
    }

    private val frameFill = Paint().apply { isAntiAlias = false }
    private val frameText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    private val frameArea = android.graphics.RectF()
    // Nombre del skin en mayúsculas y su ancho: se recalculan solo si cambian el skin o el tamaño
    private var labelSource = ""
    private var labelBase = 0f
    private var labelRoom = 0f
    private var labelSize = 0f
    private var label = ""
    private var labelWidth = 0f

    private fun rect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        frameFill.color = color
        canvas.drawRect(l, t, r, b, frameFill)
    }

    /**
     * Ventana del skin alrededor del analizador: cara con relieve, barra de título con el color de
     * acento y el nombre del skin con su letra, y la pantalla hundida con el color del marco.
     * Devuelve el área que queda para el analizador.
     */
    private fun drawWinampFrame(canvas: Canvas, w: Float, h: Float, skin: WinampSkin, density: Float): android.graphics.RectF {
        val c = skin.chrome
        val face = c[WinampChrome.FACE]
        val light = WinampColors.mix(face, 0xFFFFFFFF.toInt(), 0.35f)
        val dark = WinampColors.mix(face, 0xFF000000.toInt(), 0.5f)
        val line = maxOf(1f, density)
        val pad = maxOf(3f * line, minOf(w, h) * 0.07f)
        val title = maxOf(pad, h * 0.16f)

        // Cara con relieve: luz arriba e izquierda, sombra abajo y derecha.
        rect(canvas, 0f, 0f, w, h, face)
        rect(canvas, 0f, 0f, w, line, light)
        rect(canvas, 0f, 0f, line, h, light)
        rect(canvas, 0f, h - line, w, h, dark)
        rect(canvas, w - line, 0f, w, h, dark)

        // Barra de título: rayas del color de acento y el nombre en la letra de la pantalla.
        val accent = c[WinampChrome.ACCENT]
        val ty = title / 2 + line / 2
        // Nombre del skin en mayúsculas; si no cabe en la barra de título se achica la letra en vez
        // de salirse del marco. Se recalcula solo si cambian el skin, el tamaño o el espacio.
        val baseSize = title * 0.62f
        val room = w - 4 * pad
        // Lo que suena y el tiempo («ARTISTA - TÍTULO  1:23/3:45»); sin música, el nombre del skin
        val track = NowPlaying.track.value
        val source = track?.let { t ->
            val name = if (t.artist.isBlank()) t.title else "${t.artist} - ${t.title}"
            val pos = PlayingTrack.clock(t.positionAt(android.os.SystemClock.elapsedRealtime()))
            if (t.durationMs > 0) "$name  $pos/${PlayingTrack.clock(t.durationMs)}" else "$name  $pos"
        } ?: skin.name
        if (labelSource != source || labelBase != baseSize || labelRoom != room) {
            labelSource = source
            labelBase = baseSize
            labelRoom = room
            label = source.uppercase()
            frameText.textSize = baseSize
            var natural = frameText.measureText(label)
            // Se achica hasta el 70 %; si aún no cabe, se corta el nombre con «…» y se deja el tiempo
            labelSize = if (natural > room && natural > 0f) maxOf(baseSize * room / natural, baseSize * 0.7f) else baseSize
            frameText.textSize = labelSize
            natural = frameText.measureText(label)
            if (natural > room) {
                val tail = label.substringAfterLast("  ", "")
                var head = label.removeSuffix(tail).trimEnd()
                while (head.isNotEmpty() && frameText.measureText("$head…  $tail") > room) head = head.dropLast(1)
                label = if (tail.isEmpty()) "$head…" else "$head…  $tail"
            }
            labelWidth = frameText.measureText(label)
        }
        frameText.textSize = labelSize
        val showText = frameText.textSize >= 7f * density
        val textW = if (showText) labelWidth + 2 * pad else 0f
        val mid = w / 2
        val stripeL = pad; val stripeR = w - pad
        for (k in -1..1 step 2) {
            val y = ty + k * line * 1.5f
            if (showText) {
                rect(canvas, stripeL, y - line / 2, mid - textW / 2, y + line / 2, accent)
                rect(canvas, mid + textW / 2, y - line / 2, stripeR, y + line / 2, accent)
            } else rect(canvas, stripeL, y - line / 2, stripeR, y + line / 2, accent)
        }
        if (showText) {
            val tw = textW - pad
            rect(canvas, mid - tw / 2, ty - title * 0.4f, mid + tw / 2, ty + title * 0.4f, c[WinampChrome.TEXT_BG])
            frameText.color = c[WinampChrome.TEXT]
            frameText.textAlign = Paint.Align.CENTER
            val fm = frameText.fontMetrics
            canvas.drawText(label, mid, ty - (fm.ascent + fm.descent) / 2, frameText)
        }

        // Pantalla hundida: marco del skin, sombra arriba e izquierda, luz abajo y derecha.
        val l = pad; val t = title + line; val r = w - pad; val b = h - pad
        val frame = c[WinampChrome.FRAME]
        val bw = maxOf(line, pad * 0.35f)
        rect(canvas, l - bw, t - bw, r + bw, b + bw, frame)
        rect(canvas, l - bw, t - bw, r + bw, t - bw + line, dark)
        rect(canvas, l - bw, t - bw, l - bw + line, b + bw, dark)
        rect(canvas, l - bw, b + bw - line, r + bw, b + bw, light)
        rect(canvas, r + bw - line, t - bw, r + bw, b + bw, light)
        frameArea.set(l, t, r, b)
        return frameArea
    }

    // ---------------------------------------------------------------- Onda y circular

    private fun drawWave(canvas: Canvas, w: Float, h: Float, wave: FloatArray?, s: SpectrumSettings, density: Float) {
        val mid = h / 2
        path.reset()
        val data = wave ?: FloatArray(2)
        val pts = data.size.coerceAtLeast(2)
        for (i in 0 until pts) {
            val x = i * w / (pts - 1)
            val y = mid - SpectrumShape.applySensitivity(kotlin.math.abs(data[i]), s.sensitivity) * kotlin.math.sign(data[i]) * mid * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (waveShaderKey != s.palette to w) {
            waveShader = LinearGradient(
                0f, 0f, w, 0f, s.palette.stops.map { it.second }.toIntArray(),
                s.palette.stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP
            )
            waveShaderKey = s.palette to w
        }
        stroke.shader = waveShader
        // Brillo: un trazo ancho y tenue debajo del trazo fino.
        stroke.strokeWidth = 8f * density
        stroke.alpha = 50
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = 2.5f * density
        stroke.alpha = 255
        canvas.drawPath(path, stroke)
        stroke.shader = null
        if (s.grid) canvas.drawLine(0f, mid, w, mid, grid)
    }

    /** Barras alrededor de un círculo, simétricas a izquierda y derecha. */
    private fun drawCircle(canvas: Canvas, w: Float, h: Float, values: FloatArray, s: SpectrumSettings, density: Float) {
        val n = values.size
        if (n == 0) return
        val cx = w / 2
        val cy = h / 2
        val size = minOf(w, h)
        val r0 = size * 0.22f
        val len = size * 0.27f
        val total = n * 2
        stroke.strokeWidth = (2 * Math.PI * r0 / total * 0.6).toFloat().coerceAtLeast(1.5f * density)
        stroke.strokeCap = Paint.Cap.ROUND
        for (k in 0 until total) {
            val i = if (k < n) k else total - 1 - k
            val v = SpectrumShape.applySensitivity(values[i], s.sensitivity)
            val ang = (-Math.PI / 2 + 2 * Math.PI * k / total).toFloat()
            val cos = kotlin.math.cos(ang); val sin = kotlin.math.sin(ang)
            stroke.color = s.palette.color(v)
            val l = len * v + 2 * density
            canvas.drawLine(cx + cos * r0, cy + sin * r0, cx + cos * (r0 + l), cy + sin * (r0 + l), stroke)
        }
        fill.color = (s.palette.color(values.average().toFloat()) and 0x00FFFFFF) or 0x33000000
        canvas.drawCircle(cx, cy, r0 * 0.85f, fill)
    }

    private fun drawCaps(canvas: Canvas, n: Int, bw: Float, gap: Float, h: Float, p: (Int) -> Float, capH: Float, color: Int) {
        fill.color = color
        for (i in 0 until n) {
            val level = p(i)
            if (level < 0.04f) continue
            val x = i * bw + gap / 2
            val y = h - level * h
            canvas.drawRect(x, y - capH, x + bw - gap, y, fill)
        }
    }

    private companion object {
        /** «  ***  » entre vueltas del título, como en Winamp. */
        val MARQUEE_GAP: IntArray = "  ***  ".map { WinampWindow.glyph(it).let { (c, r) -> c or (r shl 8) } }.toIntArray()
    }
}
