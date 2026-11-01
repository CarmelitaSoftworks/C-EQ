package cl.ecualizador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews

/**
 * Reproductor de Winamp en la barra de notificaciones, como el de Spotify: la ventana del skin
 * elegido con lo que suena (título, tiempo, posición y volumen) y sus botones funcionando. Lo
 * mantiene al día [NowPlayingService] (el mismo acceso que «lo que suena»).
 */
object PlayerNotification {
    /** Prioridad normal sin sonido (el canal anterior, «player», era de prioridad baja: «Silenciadas»). */
    private const val CHANNEL = "player_winamp"
    private const val OLD_CHANNEL = "player"
    private const val ID = 7
    private const val PREFS = "player"

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("notification", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("notification", on).apply()
        update(ctx)
    }

    private val renderer by lazy { SpectrumRenderer() }

    /** Publica, actualiza o quita la notificación según lo que suene. */
    @Synchronized
    fun update(ctx: Context) {
        // El widget del reproductor se actualiza al mismo ritmo, haya o no notificación
        runCatching { PlayerWidget.update(ctx) }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val track = NowPlaying.track.value
        if (!isEnabled(ctx) || track == null || NowPlaying.controller == null) {
            nm.cancel(ID)
            return
        }
        SpectrumPrefs.load(ctx)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            // Android no deja subir la prioridad de un canal existente: se crea otro y se borra el viejo.
            // Si el usuario había desactivado el viejo, el nuevo también nace desactivado.
            val blocked = nm.getNotificationChannel(OLD_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE
            runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) }
            val importance = if (blocked) NotificationManager.IMPORTANCE_NONE else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.player_channel), importance).apply {
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }
            )
        }
        val skin = WinampSkins.byName(SpectrumPrefs.settings.value.winampSkin)
        val window = windowBitmap(ctx)

        val expanded = RemoteViews(ctx.packageName, R.layout.player_expanded).apply {
            setImageViewBitmap(R.id.player_window, window)
            setOnClickPendingIntent(R.id.player_display, action(ctx, "toggle"))
            setOnClickPendingIntent(R.id.player_previous, action(ctx, "previous"))
            setOnClickPendingIntent(R.id.player_play, action(ctx, "play"))
            setOnClickPendingIntent(R.id.player_pause, action(ctx, "pause"))
            setOnClickPendingIntent(R.id.player_stop, action(ctx, "stop"))
            setOnClickPendingIntent(R.id.player_next, action(ctx, "next"))
            openPlayer(ctx)?.let { setOnClickPendingIntent(R.id.player_eject, it) }
        }
        val playing = track.state == PlayingTrack.State.PLAYING
        val pos = PlayingTrack.clock(track.positionAt(android.os.SystemClock.elapsedRealtime()))
        val time = if (track.durationMs > 0) "$pos / ${PlayingTrack.clock(track.durationMs)}" else pos
        val collapsed = RemoteViews(ctx.packageName, R.layout.player_collapsed).apply {
            setTextViewText(R.id.player_title, track.title.ifBlank { track.winampTitle() })
            setTextViewText(R.id.player_subtitle, if (track.artist.isBlank()) time else "${track.artist} · $time")
            buttons(skin, playing)?.let { setImageViewBitmap(R.id.player_buttons, it) }
            setOnClickPendingIntent(R.id.player_previous_small, action(ctx, "previous"))
            setOnClickPendingIntent(R.id.player_toggle_small, action(ctx, "toggle"))
            setOnClickPendingIntent(R.id.player_next_small, action(ctx, "next"))
        }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentTitle(track.winampTitle())
            .setContentIntent(openPlayer(ctx))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }

    /**
     * La ventana del skin elegido con lo que suena, al doble, para la notificación y el widget. Sin
     * desplazar el título: es una foto por segundo y quedaría cortado a la mitad.
     */
    internal fun windowBitmap(ctx: Context): Bitmap {
        val skinName = SpectrumPrefs.load(ctx).winampSkin
        val settings = SpectrumSettings(style = SpectrumStyle.WINAMP_CLASSIC, winampSkin = skinName, winampFrame = true, winampMarquee = false)
        val bars = if (Spectrum.running.value) SpectrumShape.resample(Spectrum.bars.value, settings.effectiveBars) else FloatArray(settings.effectiveBars)
        // RGB 565: la mitad de memoria; las notificaciones y los widgets tienen un límite de tamaño
        return render(2, WinampWindow.Part.FULL, settings, bars)
    }

    private fun render(scale: Int, part: WinampWindow.Part, s: SpectrumSettings, bars: FloatArray): Bitmap {
        val bmp = Bitmap.createBitmap(part.w * scale, part.h * scale, Bitmap.Config.RGB_565)
        renderer.draw(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), bars, null, s.copy(winampPart = part))
        return bmp
    }

    /** Botones de la tira plegada, en orden; sus anchos son los pesos de las zonas de player_collapsed.xml. */
    fun stripButtons(playing: Boolean) =
        listOf(WinampWindow.Button.PREVIOUS, if (playing) WinampWindow.Button.PAUSE else WinampWindow.Button.PLAY, WinampWindow.Button.NEXT)

    private var stripKey: Triple<String, Boolean, Bitmap>? = null
    private var strip: Bitmap? = null

    /**
     * Tira de botones del skin, tal como se ven en su ventana y juntos: anterior, reproducir (o pausa
     * si suena) y siguiente, al triple para que se vean nítidos. Se rearma solo si cambian el skin o
     * el estado (la notificación se actualiza cada segundo).
     */
    private fun buttons(skin: WinampSkin, playing: Boolean): Bitmap? {
        val atlas = WinampWindows.bitmap(skin) ?: return null
        val key = Triple(skin.name, playing, atlas)
        if (key == stripKey) strip?.let { return it }
        val parts = stripButtons(playing)
        val raw = Bitmap.createBitmap(parts.sumOf { it.w }, parts.maxOf { it.h }, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        var x = 0f
        for (b in parts) {
            c.drawBitmap(atlas, android.graphics.Rect(b.x, b.y, b.x + b.w, b.y + b.h), android.graphics.RectF(x, 0f, x + b.w, b.h.toFloat()), null)
            x += b.w
        }
        val scaled = Bitmap.createScaledBitmap(raw, raw.width * 3, raw.height * 3, false)
        if (scaled !== raw) raw.recycle()
        stripKey = key
        strip = scaled
        return scaled
    }

    /**
     * Abre la app de música directamente (desde Android 12 una notificación no puede abrir apps
     * pasando por un receptor): su propia pantalla de reproducción o, si no la declara, su ícono.
     */
    internal fun openPlayer(ctx: Context): PendingIntent? {
        val c = NowPlaying.controller ?: return null
        c.sessionActivity?.let { return it }
        val pkg = c.packageName ?: return null
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        return PendingIntent.getActivity(ctx, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun action(ctx: Context, name: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, name.hashCode(),
        Intent(ctx, PlayerActionReceiver::class.java).setAction("cl.ecualizador.player.$name"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Lo que hace cada botón de la notificación. */
    fun hitFor(action: String?): WinampWindow.Hit? = when (action?.substringAfterLast('.')) {
        "previous" -> WinampWindow.Hit.Press(WinampWindow.Button.PREVIOUS)
        "play" -> WinampWindow.Hit.Press(WinampWindow.Button.PLAY)
        "pause" -> WinampWindow.Hit.Press(WinampWindow.Button.PAUSE)
        "stop" -> WinampWindow.Hit.Press(WinampWindow.Button.STOP)
        "next" -> WinampWindow.Hit.Press(WinampWindow.Button.NEXT)
        "toggle" -> WinampWindow.Hit.TogglePlay
        else -> null
    }
}

/** Botones del reproductor de la notificación. */
class PlayerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hit = PlayerNotification.hitFor(intent.action) ?: return
        NowPlaying.perform(context, hit)
    }
}
