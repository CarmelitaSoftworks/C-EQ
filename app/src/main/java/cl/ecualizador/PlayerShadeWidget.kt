package cl.ecualizador

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget de la ventana plegada de Winamp («shade»): una barra delgada con el mini visor, el reloj y
 * los mini botones, que ocupa una sola fila de la pantalla de inicio. Se actualiza junto con el
 * reproductor de la notificación (ver PlayerNotification.update).
 */
class PlayerShadeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PlayerNotification.update(context)

    companion object {
        fun ids(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx)?.getAppWidgetIds(ComponentName(ctx, PlayerShadeWidget::class.java)) ?: IntArray(0)

        /** Muestra en [ids] la barra ya dibujada ([shade], ver PlayerNotification.shadeBitmap). */
        fun push(ctx: Context, ids: IntArray, shade: android.graphics.Bitmap) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            if (ids.isEmpty()) return
            val app = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val access = NowPlaying.hasAccess(ctx)
            fun tap(name: String) = if (access) PlayerNotification.action(ctx, name) else app
            val views = RemoteViews(ctx.packageName, R.layout.player_shade).apply {
                setImageViewBitmap(R.id.shade_window, shade)
                setOnClickPendingIntent(R.id.shade_display, tap("toggle"))
                setOnClickPendingIntent(R.id.shade_previous, tap("previous"))
                setOnClickPendingIntent(R.id.shade_play, tap("play"))
                setOnClickPendingIntent(R.id.shade_pause, tap("pause"))
                setOnClickPendingIntent(R.id.shade_stop, tap("stop"))
                setOnClickPendingIntent(R.id.shade_next, tap("next"))
                setOnClickPendingIntent(R.id.shade_eject, PlayerNotification.openPlayer(ctx) ?: app)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
