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

    companion object {
        /** Los widgets del reproductor puestos en la pantalla de inicio. */
        fun ids(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx)?.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)) ?: IntArray(0)

        /** Muestra en [ids] la ventana ya dibujada ([window], ver PlayerNotification.windowBitmap). */
        fun push(ctx: Context, ids: IntArray, window: android.graphics.Bitmap) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            if (ids.isEmpty()) return
            val app = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val access = NowPlaying.hasAccess(ctx)
            fun tap(name: String) = if (access) PlayerNotification.action(ctx, name) else app
            val views = RemoteViews(ctx.packageName, R.layout.player_expanded).apply {
                setImageViewBitmap(R.id.player_window, window)
                setOnClickPendingIntent(R.id.player_display, tap("toggle"))
                setOnClickPendingIntent(R.id.player_previous, tap("previous"))
                setOnClickPendingIntent(R.id.player_play, tap("play"))
                setOnClickPendingIntent(R.id.player_pause, tap("pause"))
                setOnClickPendingIntent(R.id.player_stop, tap("stop"))
                setOnClickPendingIntent(R.id.player_next, tap("next"))
                setOnClickPendingIntent(R.id.player_eject, PlayerNotification.openPlayer(ctx) ?: app)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
