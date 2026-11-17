package org.regnant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.regnant.android.MainActivity
import org.regnant.android.R
import org.regnant.android.RegnantApp

class RegnantForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "regnant_connected_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "org.regnant.android.action.START"
        const val ACTION_STOP = "org.regnant.android.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RegnantForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RegnantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            val app = application as? RegnantApp
            app?.client?.disconnect()
            return START_NOT_STICKY
        }

        val app = application as? RegnantApp
        val host = app?.securityStore?.getPairedHost()
        val hostName = host?.hostName ?: "Omarchy Laptop"

        val notification = buildNotification("Connected to $hostName")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        app?.client?.connect()

        return START_STICKY
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, RegnantForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingDisconnectIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Regnant Mobile Control")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(0, "Disconnect", pendingDisconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Regnant Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active local connection to Linux workstation"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
