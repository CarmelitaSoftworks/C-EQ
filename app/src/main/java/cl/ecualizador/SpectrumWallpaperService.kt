package cl.ecualizador

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder

/**
 * Fondo de pantalla animado: tu foto con el espectro encima, dibujado en cada fotograma de la
 * pantalla (60 fps o más). Es la forma en que Android permite animación continua en el inicio.
 * Solo trabaja mientras el fondo está visible.
 */
class SpectrumWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SpectrumEngine()

    private companion object {
        /** Tiempo que se considera abierto el cajón de apps tras abrirlo o tocarlo. */
        const val DRAWER_HOLD_MS = 5000L
        /** Doble toque: el segundo, antes de este tiempo y a esta distancia del primero. */
        const val DOUBLE_TAP_MS = 450L
        const val DOUBLE_TAP_SLOP_DP = 32f
    }

    inner class SpectrumEngine : Engine(), Choreographer.FrameCallback {
        private val renderer = SpectrumRenderer()
        /** Cada motor (inicio, bloqueo, vista previa) se registra aparte para no apagar a los demás. */
        private val spectrumUser = "wallpaper@${System.identityHashCode(this)}"
        private var smoother: SpectrumSmoother? = null
        private var visible = false
        private var width = 0
        private var height = 0
        private var lastFrameNanos = 0L
        private var background: Bitmap? = null
        private var backgroundVersion = -1L
        /** Ya se pidió una superficie del tamaño de la pantalla (se hace una sola vez). */
        private var enlarged = false
        /** Dónde se dibujó el visor en el último cuadro, para traducir los toques. */
        private var drawnX = 0f
        private var drawnY = 0f
        private var accessHintShown = false
        private var error: String? = null
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dimPaint = Paint()
        private var gradientShader: Shader? = null
        private var gradientHeight = -1
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textAlign = Paint.Align.CENTER }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Algunos teléfonos crean el fondo en 16 bits (RGB 565): los degradados de la foto se ven con escalones.
            surfaceHolder.setFormat(PixelFormat.RGBA_8888)
            // Para que los botones del skin respondan aunque el launcher no avise de los toques
            setTouchEventsEnabled(true)
            // La ventana del ecualizador del skin completo muestra y cambia el ecualizador
            EqEngine.get(this@SpectrumWallpaperService)
            // Skin al azar al encender la pantalla
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    this@SpectrumWallpaperService, screenOn, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        }

        private val screenOn = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (!isPreview) SkinShuffle.onScreenOn(context)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            width = w; height = h
            backgroundVersion = -1 // reescalar la foto al nuevo tamaño
            val (dw, dh) = displaySize()
            // Algunas capas de fabricante dan al fondo animado una superficie más chica que la pantalla
            // y luego la amplían: la foto se ve pixelada. Se pide una vez una del tamaño real.
            val full = if (!enlarged) WallpaperImage.fullResolutionSize(w, h, dw, dh) else null
            if (full != null) {
                enlarged = true
                holder.setFixedSize(full.first, full.second)
            }
            WallpaperPrefs.reportRender(this@SpectrumWallpaperService, WallpaperRender(w, h, dw, dh, enlarged))
            if (full == null) drawFrame()
        }

        /**
         * Toques en la pantalla de inicio. Hay dos caminos: Android entrega al fondo los toques de la
         * pantalla que lo deja ver ([onTouchEvent], también los de los íconos y del cajón de apps) y
         * algunos launchers avisan de los toques en zonas sin íconos (COMMAND_TAP). Si el launcher
         * avisa, se le cree solo a él; si no (como el de vivo), se usan los toques directos, sin los
         * que llegan con el cajón de apps abierto.
         */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: android.os.Bundle?, resultRequested: Boolean): android.os.Bundle? {
            if (action == android.app.WallpaperManager.COMMAND_TAP) {
                launcherTaps = true
                tap(x.toFloat(), y.toFloat())
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        /** El launcher avisa de los toques en zonas libres: los toques directos ya no hacen falta. */
        private var launcherTaps = false
        /**
         * El cajón de apps (u otra vista del launcher) tapa el inicio: lo dice el zoom del fondo o un
         * deslizamiento hacia arriba, hasta [DRAWER_HOLD_MS] sin tocar o hasta salir y volver al inicio.
         */
        private var zoomed = false
        private var drawerUntil = 0L

        /** Android 11+: los launchers acercan el fondo al abrir el cajón de apps o las apps recientes. */
        override fun onZoomChanged(zoom: Float) {
            zoomed = zoom > 0.05f
        }

        private fun drawerOpen() = zoomed || android.os.SystemClock.uptimeMillis() < drawerUntil

        /** Cada dedo por separado: dónde y cuándo bajó, y si ya se movió (no es un toque). */
        private inner class Finger(val x: Float, val y: Float, val at: Long) { var moved = false }
        private val fingers = android.util.SparseArray<Finger>()
        private var lastTapAt = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f

        override fun onTouchEvent(event: android.view.MotionEvent) {
            val slop = android.view.ViewConfiguration.get(this@SpectrumWallpaperService).scaledTouchSlop * 1.5f
            val now = android.os.SystemClock.uptimeMillis()
            // Mientras se usa el cajón, cada toque lo mantiene «abierto» un rato más
            if (drawerUntil > now) drawerUntil = now + DRAWER_HOLD_MS
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = event.actionIndex
                    fingers.put(event.getPointerId(i), Finger(event.getX(i), event.getY(i), event.eventTime))
                }
                android.view.MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                    val f = fingers[event.getPointerId(i)] ?: continue
                    val dx = event.getX(i) - f.x
                    val dy = event.getY(i) - f.y
                    if (kotlin.math.hypot(dx, dy) > slop) f.moved = true
                    // Deslizar hacia arriba abre el cajón de apps. Hacia abajo no se da por cerrado: dentro
                    // del cajón también se desliza así para recorrer la lista; se cierra solo, sin toques
                    if (dy < -slop * 4 && -dy > kotlin.math.abs(dx) * 1.5f) drawerUntil = now + DRAWER_HOLD_MS
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_POINTER_UP -> {
                    val i = event.actionIndex
                    val id = event.getPointerId(i)
                    val f = fingers[id]
                    fingers.remove(id)
                    if (f != null && !f.moved && event.eventTime - f.at < 500 && !launcherTaps && !drawerOpen()) {
                        tap(event.getX(i), event.getY(i))
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> fingers.clear()
            }
            super.onTouchEvent(event)
        }

        /** Primer toque de un doble toque: cuándo y dónde, y qué del skin tocó. */
        private var armedAt = 0L
        private var armedX = 0f
        private var armedY = 0f
        private var armedHit: WinampWindow.Hit? = null

        private fun tap(x: Float, y: Float) {
            val now = android.os.SystemClock.uptimeMillis()
            // El mismo toque por los dos caminos cuenta una vez; toques seguidos en otro lugar, todos
            if (now - lastTapAt < 250 && kotlin.math.hypot(x - lastTapX, y - lastTapY) < 40f) return
            lastTapAt = now; lastTapX = x; lastTapY = y
            val mode = WallpaperPrefs.load(this@SpectrumWallpaperService).touch
            if (mode == WallpaperTouch.OFF) return
            val hit = renderer.hitAt(x - drawnX, y - drawnY) ?: return
            if (mode == WallpaperTouch.DOUBLE) {
                val second = armedHit != null && now - armedAt < DOUBLE_TAP_MS &&
                    kotlin.math.hypot(x - armedX, y - armedY) < DOUBLE_TAP_SLOP_DP * resources.displayMetrics.density &&
                    WallpaperTouches.sameTarget(armedHit!!, hit)
                if (!second) {
                    armedAt = now; armedX = x; armedY = y; armedHit = hit
                    return
                }
                armedHit = null
            }
            val ctx = this@SpectrumWallpaperService
            // El volumen y el ecualizador no dependen del reproductor que suena
            val needsSession = hit !is WinampWindow.Hit.Volume && hit != WinampWindow.Hit.OpenEqualizer &&
                hit !is WinampWindow.Hit.EqBand && hit != WinampWindow.Hit.EqToggle && hit != WinampWindow.Hit.EqAuto && hit != WinampWindow.Hit.EqPresets
            when {
                needsSession && !NowPlaying.hasAccess(ctx) -> if (!accessHintShown) {
                    accessHintShown = true
                    android.widget.Toast.makeText(ctx, "Para usar los botones, activa «Mostrar lo que suena» en Ecualizador", android.widget.Toast.LENGTH_LONG).show()
                }
                NowPlaying.perform(ctx, hit) -> (hit as? WinampWindow.Hit.Press)?.let { renderer.press(it.button) }
                needsSession -> android.widget.Toast.makeText(ctx, "No hay música abierta", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        /** Resolución física de la pantalla (no la de la ventana ni la escalada). */
        private fun displaySize(): Pair<Int, Int> {
            val display = getSystemService(android.hardware.display.DisplayManager::class.java)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return 0 to 0
            val mode = display.mode
            return mode.physicalWidth to mode.physicalHeight
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            drawerUntil = 0L
            // La vista previa del selector de fondos no cambia el skin
            if (isVisible && !isPreview) SkinShuffle.onShown(this@SpectrumWallpaperService)
            fingers.clear()
            if (isVisible) {
                NowPlaying.readVolume(this@SpectrumWallpaperService)
                error = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    "Abre Ecualizador y activa «Espectro en tiempo real» para dar el permiso"
                } else {
                    Spectrum.acquire(spectrumUser, this@SpectrumWallpaperService)
                }
                lastFrameNanos = 0
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                Choreographer.getInstance().removeFrameCallback(this)
                Spectrum.release(spectrumUser)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            Choreographer.getInstance().removeFrameCallback(this)
            Spectrum.release(spectrumUser)
        }

        override fun onDestroy() {
            runCatching { unregisterReceiver(screenOn) }
            Choreographer.getInstance().removeFrameCallback(this)
            Spectrum.release(spectrumUser)
            background?.recycle()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            val settings = WallpaperPrefs.load(this@SpectrumWallpaperService).view
                .spectrumSettings(SpectrumPrefs.load(this@SpectrumWallpaperService))
            val dt = if (lastFrameNanos == 0L) 16L else (frameTimeNanos - lastFrameNanos) / 1_000_000
            lastFrameNanos = frameTimeNanos
            val s = smoother?.takeIf { it.size == settings.effectiveBars } ?: SpectrumSmoother(settings.effectiveBars).also { smoother = it }
            s.fallPerSec = settings.fall.perSec
            s.step(SpectrumShape.resample(Spectrum.bars.value, settings.effectiveBars), dt)
            drawFrame()
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun loadBackground(ws: WallpaperSettings) {
            if (ws.imageVersion == backgroundVersion || width == 0) return
            background?.recycle()
            background = null
            backgroundVersion = ws.imageVersion
            val file = WallpaperPrefs.currentImage(this@SpectrumWallpaperService) ?: return
            if (!ws.hasImage) return
            // La foto original se decodifica cerca del tamaño de la pantalla (nunca por debajo), se
            // endereza y se reduce por pasos (ver WallpaperImage).
            val (uw, uh) = WallpaperImage.uprightSize(file.path) ?: return
            val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = WallpaperImage.sampleSizeFor(uw, uh, width, height)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return
            val upright = WallpaperImage.rotate(src, WallpaperImage.exifDegrees(file.path))
            background = WallpaperImage.cover(upright, width, height)
            upright.recycle()
        }

        private fun drawFrame() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val ws = WallpaperPrefs.load(this@SpectrumWallpaperService)
                val settings = SpectrumPrefs.load(this@SpectrumWallpaperService)
                loadBackground(ws)
                val bg = background
                if (bg != null) {
                    canvas.drawBitmap(bg, 0f, 0f, bitmapPaint)
                    dimPaint.color = ((ws.dim.coerceIn(0f, 0.7f) * 255).toInt() shl 24)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
                } else {
                    if (gradientHeight != height) {
                        gradientShader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF1A1033.toInt(), 0xFF0F1115.toInt(), Shader.TileMode.CLAMP)
                        gradientHeight = height
                    }
                    dimPaint.shader = gradientShader
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
                    dimPaint.shader = null
                }
                val density = resources.displayMetrics.density
                val margin = 16 * density
                val spectrum = ws.view.spectrumSettings(settings)
                // El reproductor ocupa todo el ancho con su proporción; el espectro solo, el alto elegido
                val h = ws.view.height(settings, width - 2 * margin, height.toFloat(), ws.height)
                val top = when (ws.position) {
                    WallpaperPosition.TOP -> height * 0.12f
                    WallpaperPosition.CENTER -> (height - h) / 2
                    WallpaperPosition.BOTTOM -> height - h - height * 0.14f // por encima de la barra de apps
                }
                error?.let { msg ->
                    text.textSize = 15 * density
                    canvas.drawText(msg, width / 2f, top + h / 2, text)
                }
                smoother?.let { s ->
                    drawnX = margin; drawnY = top
                    canvas.save()
                    canvas.translate(margin, top)
                    renderer.draw(canvas, width - 2 * margin, h, s.values, s.peaks, spectrum, density, Spectrum.wave.value)
                    canvas.restore()
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
