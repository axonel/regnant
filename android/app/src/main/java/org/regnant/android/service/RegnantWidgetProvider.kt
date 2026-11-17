package org.regnant.android.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.regnant.android.MainActivity
import org.regnant.android.R

class RegnantWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_regnant).apply {
                setOnClickPendingIntent(R.id.widget_btn_action, pendingIntent)
                setTextViewText(R.id.widget_status, "Ready • Connected")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
