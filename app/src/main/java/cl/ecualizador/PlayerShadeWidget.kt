package cl.ecualizador

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

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
        fun push(ctx: Context, ids: IntArray, shade: android.graphics.Bitmap) = PlayerWidget.push(
            ctx, ids, R.layout.player_shade, R.id.shade_window, shade,
            PlayerWidget.Buttons(R.id.shade_display, R.id.shade_previous, R.id.shade_play, R.id.shade_pause, R.id.shade_stop, R.id.shade_next, R.id.shade_eject),
        )
    }
}
