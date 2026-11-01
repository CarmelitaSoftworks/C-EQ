package cl.ecualizador

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

/** Lo que suena: título, artista y dónde va la canción. */
data class PlayingTrack(
    val title: String,
    val artist: String,
    val durationMs: Long,
    /** Posición cuando el reproductor la informó, en [updatedAt] (SystemClock.elapsedRealtime). */
    val positionMs: Long,
    val updatedAt: Long,
    val speed: Float,
    val state: State,
    /** Género, si el reproductor lo informa (casi solo los de archivos locales). */
    val genre: String = "",
    /** App que reproduce. */
    val app: String = "",
) {
    enum class State { PLAYING, PAUSED, STOPPED }

    /** Posición ahora, avanzando sola mientras suena (como hace el propio Android). */
    fun positionAt(now: Long): Long {
        val p = if (state == State.PLAYING) positionMs + ((now - updatedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }

    /** Como en Winamp: «Artista - Título (3:45)». */
    fun winampTitle(): String {
        val name = if (artist.isBlank()) title else "$artist - $title"
        return if (durationMs > 0) "$name (${clock(durationMs)})" else name
    }

    companion object {
        fun clock(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
}

/** Una canción de la cola del reproductor. */
data class QueueEntry(val id: Long, val title: String, val artist: String)

/**
 * La canción que suena en cualquier app, leída de las sesiones multimedia de Android. Para eso
 * Android exige el acceso a las notificaciones ([NowPlayingService]); sin él, [track] queda en null.
 */
object NowPlaying {
    val track = MutableStateFlow<PlayingTrack?>(null)

    /** Carátula de lo que suena, si el reproductor la entrega como imagen (Spotify lo hace). */
    val art = MutableStateFlow<android.graphics.Bitmap?>(null)

    /** La carátula de la sesión, reducida a [max] px de lado como mucho (las notificaciones tienen un límite). */
    fun artFrom(c: MediaController, max: Int = 512): android.graphics.Bitmap? {
        val md = c.metadata ?: return null
        val bmp = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .firstNotNullOfOrNull { runCatching { md.getBitmap(it) }.getOrNull() } ?: return null
        val side = maxOf(bmp.width, bmp.height)
        if (side <= max) return bmp
        val k = max / side.toFloat()
        return android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
    }

    /**
     * La cola del reproductor que suena, si la publica (Spotify y otros lo hacen para Android Auto);
     * vacía si no. [activeQueueId] es la que suena (−1 si no se sabe).
     */
    val queue = MutableStateFlow<List<QueueEntry>>(emptyList())

    @Volatile
    var activeQueueId: Long = -1

    /** Lo que muestra la lista de reproducción del skin: la cola o, si no hay, la canción actual. */
    fun playlist(): List<QueueEntry> = queue.value.ifEmpty {
        track.value?.let { listOf(QueueEntry(-1, it.title, it.artist)) }.orEmpty()
    }

    /** Posición de la canción que suena en [list] (o −1). */
    fun activeIndex(list: List<QueueEntry>): Int =
        list.indexOfFirst { it.id == activeQueueId && it.id != -1L }.takeIf { it >= 0 } ?: if (list.size == 1 && list[0].id == -1L) 0 else -1

    fun queueFrom(c: MediaController): List<QueueEntry> = c.queue.orEmpty().map { item ->
        val d = item.description
        QueueEntry(item.queueId, d.title?.toString().orEmpty(), d.subtitle?.toString().orEmpty())
    }

    /** Si Android tiene conectado [NowPlayingService] (con el permiso dado, debería estarlo). */
    val connected = MutableStateFlow(false)

    /**
     * Algunos teléfonos desconectan el servicio al actualizar la app y no lo reconectan hasta
     * reiniciar, aunque el permiso siga dado: se le pide a Android que lo vuelva a conectar.
     */
    fun reconnect(ctx: Context) {
        if (connected.value || !hasAccess(ctx)) return
        runCatching { NotificationListenerService.requestRebind(ComponentName(ctx, NowPlayingService::class.java)) }
    }

    /** La sesión que se muestra (la que reproduce, o la más reciente), para los botones del skin. */
    @Volatile
    var controller: MediaController? = null

    /** Volumen de la música (0..1), o −1 si aún no se leyó. Lo actualiza [readVolume]. */
    @Volatile
    var volume: Float = -1f

    fun readVolume(ctx: Context) {
        val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    /**
     * Ejecuta lo que se tocó en el skin. El volumen y el EQ no dependen de que haya música; lo demás
     * actúa sobre el reproductor que suena (falso si no hay ninguno).
     */
    fun perform(ctx: Context, hit: WinampWindow.Hit): Boolean {
        when (hit) {
            is WinampWindow.Hit.Volume -> {
                val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return false
                val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(hit.fraction * max), 0)
                readVolume(ctx)
                return true
            }
            WinampWindow.Hit.OpenEqualizer -> return open(ctx, android.content.Intent(ctx, MainActivity::class.java))
            is WinampWindow.Hit.EqBand -> {
                EqEngine.get(ctx).setFromSkin(hit.band, hit.fraction)
                return true
            }
            WinampWindow.Hit.EqToggle -> {
                val e = EqEngine.get(ctx)
                e.setEnabled(!e.state.value.enabled)
                return true
            }
            WinampWindow.Hit.EqPresets -> {
                EqEngine.get(ctx).nextPreset()
                return true
            }
            WinampWindow.Hit.EqAuto -> {
                val e = EqEngine.get(ctx)
                e.setAuto(!e.state.value.auto)
                return true
            }
            else -> {}
        }
        val c = controller ?: return false
        if (hit == WinampWindow.Hit.OpenPlayer) {
            // La pantalla de la app de música (la que ella misma declara) o, si no, su ícono
            c.sessionActivity?.let { pi -> if (runCatching { pi.send() }.isSuccess) return true }
            val pkg = c.packageName ?: return false
            return ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { open(ctx, it) } ?: false
        }
        val t = c.transportControls
        return runCatching {
            when (hit) {
                is WinampWindow.Hit.Press -> when (hit.button) {
                    WinampWindow.Button.PREVIOUS -> t.skipToPrevious()
                    WinampWindow.Button.PLAY -> t.play()
                    WinampWindow.Button.PAUSE -> t.pause()
                    WinampWindow.Button.STOP -> t.stop()
                    WinampWindow.Button.NEXT -> t.skipToNext()
                }
                is WinampWindow.Hit.Seek -> {
                    val d = track.value?.durationMs ?: 0L
                    if (d > 0) t.seekTo((d * hit.fraction).toLong())
                }
                WinampWindow.Hit.TogglePlay ->
                    if (track.value?.state == PlayingTrack.State.PLAYING) t.pause() else t.play()
                is WinampWindow.Hit.PlaylistRow -> {
                    val entry = playlist().getOrNull(hit.index) ?: return false
                    if (entry.id != -1L) t.skipToQueueItem(entry.id)
                }
                else -> {}
            }
        }.isSuccess
    }

    private fun open(ctx: Context, intent: android.content.Intent): Boolean =
        runCatching { ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Si el usuario dio el acceso a las notificaciones a la app. */
    fun hasAccess(ctx: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun from(controller: MediaController): PlayingTrack? {
        val md = controller.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val ps = controller.playbackState
        val state = when (ps?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> PlayingTrack.State.PLAYING
            PlaybackState.STATE_PAUSED -> PlayingTrack.State.PAUSED
            else -> PlayingTrack.State.STOPPED
        }
        return PlayingTrack(
            title = title, artist = artist,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = ps?.position ?: 0L,
            updatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            state = state,
            genre = md.getString(MediaMetadata.METADATA_KEY_GENRE).orEmpty(),
            app = controller.packageName.orEmpty(),
        )
    }
}

/**
 * Servicio de acceso a notificaciones: no lee notificaciones, solo lo usa Android como permiso para
 * ver qué suena (MediaSessionManager.getActiveSessions). Sigue la sesión que esté reproduciendo.
 */
class NowPlayingService : NotificationListenerService() {
    private var sessions: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = refresh()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> follow(list.orEmpty()) }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastArtKey: String? = null
    /** Mientras suena, el reproductor de la notificación avanza su reloj una vez por segundo. */
    private val tick = object : Runnable {
        override fun run() {
            PlayerNotification.update(this@NowPlayingService)
            if (NowPlaying.track.value?.state == PlayingTrack.State.PLAYING) main.postDelayed(this, 1000)
        }
    }
    /** Mantener una tecla de volumen manda muchos avisos seguidos: se redibuja una vez al final. */
    private val volumeRedraw = Runnable { PlayerNotification.update(this) }
    private val volumeChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            NowPlaying.readVolume(context)
            main.removeCallbacks(volumeRedraw)
            main.postDelayed(volumeRedraw, 250)
        }
    }

    override fun onListenerConnected() {
        NowPlaying.connected.value = true
        NowPlaying.readVolume(this)
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this, volumeChanged, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = msm
        val me = ComponentName(this, NowPlayingService::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, me)
            follow(msm.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        NowPlaying.connected.value = false
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        follow(emptyList())
        NowPlaying.controller = null
        main.removeCallbacks(tick)
        main.removeCallbacks(volumeRedraw)
        runCatching { unregisterReceiver(volumeChanged) }
        PlayerNotification.cancel(this)
        sessions = null
    }

    private fun follow(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers.clear()
        controllers += list
        controllers.forEach { runCatching { it.registerCallback(callback) } }
        refresh()
    }

    /** La sesión que reproduce; si ninguna, la primera (la más reciente según Android). */
    private fun refresh() {
        val tracks = controllers.mapNotNull { c -> runCatching { NowPlaying.from(c) }.getOrNull()?.let { c to it } }
        val chosen = tracks.firstOrNull { it.second.state == PlayingTrack.State.PLAYING } ?: tracks.firstOrNull()
        NowPlaying.controller = chosen?.first
        NowPlaying.activeQueueId = chosen?.first?.let { c -> runCatching { c.playbackState?.activeQueueItemId }.getOrNull() } ?: -1
        NowPlaying.queue.value = chosen?.first?.let { c -> runCatching { NowPlaying.queueFrom(c) }.getOrNull() }.orEmpty()
        NowPlaying.track.value = chosen?.second
        // La misma carátula llega como una imagen nueva en cada aviso: solo se cambia si cambió la canción
        val artKey = chosen?.second?.let { "${it.app}|${it.artist}|${it.title}" }
        if (artKey != lastArtKey || NowPlaying.art.value == null) {
            lastArtKey = artKey
            NowPlaying.art.value = chosen?.first?.let { c -> runCatching { NowPlaying.artFrom(c) }.getOrNull() }
        }
        // El ajuste automático depende de qué suena (voz, video o música)
        EqEngine.peek()?.onMediaChanged()
        main.removeCallbacks(tick)
        tick.run()
    }
}
