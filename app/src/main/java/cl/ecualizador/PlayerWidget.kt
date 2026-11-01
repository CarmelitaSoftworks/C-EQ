package cl.ecualizador

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget del reproductor: la ventana del skin elegido con lo que suena y sus botones, como el
 * reproductor de la notificación. Lo mantiene al día [NowPlayingService] (cada segundo mientras
 * suena); sin acceso a lo que suena, tocarlo abre la app para darlo.
 */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PlayerNotification.update(context)

    /** Zonas táctiles de un widget del reproductor (ids de su layout). */
    class Buttons(val display: Int, val previous: Int, val play: Int, val pause: Int, val stop: Int, val next: Int, val eject: Int)

    companion object {
        /** Los widgets del reproductor puestos en la pantalla de inicio. */
        fun ids(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx)?.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)) ?: IntArray(0)

        /** Muestra en [ids] la ventana ya dibujada ([window], ver PlayerNotification.windowBitmap). */
        fun push(ctx: Context, ids: IntArray, window: android.graphics.Bitmap) = push(
            ctx, ids, R.layout.player_expanded, R.id.player_window, window,
            Buttons(R.id.player_display, R.id.player_previous, R.id.player_play, R.id.player_pause, R.id.player_stop, R.id.player_next, R.id.player_eject),
        )

        /**
         * Pone [image] en [imageId] del layout [layout] y conecta sus botones: los de la música si hay
         * acceso a lo que suena (si no, abren la app para darlo) y expulsar, a la app de música.
         * Lo comparten el widget del reproductor y el de la barra plegada.
         */
        internal fun push(ctx: Context, ids: IntArray, layout: Int, imageId: Int, image: android.graphics.Bitmap, b: Buttons) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            if (ids.isEmpty()) return
            val app = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val access = NowPlaying.hasAccess(ctx)
            fun tap(name: String) = if (access) PlayerNotification.action(ctx, name) else app
            val views = RemoteViews(ctx.packageName, layout).apply {
                setImageViewBitmap(imageId, image)
                setOnClickPendingIntent(b.display, tap("toggle"))
                setOnClickPendingIntent(b.previous, tap("previous"))
                setOnClickPendingIntent(b.play, tap("play"))
                setOnClickPendingIntent(b.pause, tap("pause"))
                setOnClickPendingIntent(b.stop, tap("stop"))
                setOnClickPendingIntent(b.next, tap("next"))
                setOnClickPendingIntent(b.eject, PlayerNotification.openPlayer(ctx) ?: app)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
