package org.regnant.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.SecurityStore
import org.regnant.android.network.NsdDiscovery
import org.regnant.android.network.RegnantClient

class RegnantApp : Application() {
    lateinit var securityStore: SecurityStore
    lateinit var client: RegnantClient
    lateinit var nsdDiscovery: NsdDiscovery

    private val applicationScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val NOTIFICATION_CHANNEL_ALERTS = "regnant_command_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        securityStore = SecurityStore(this)
        client = RegnantClient(securityStore)
        nsdDiscovery = NsdDiscovery(this)

        createNotificationChannels()
        observeCommandEvents()

        val pairedHost = securityStore.getPairedHost()
        if (pairedHost != null && securityStore.isAutoReconnectEnabled()) {
            client.connect()

            // Also start NSD to track dynamic IP changes on local Wi-Fi
            nsdDiscovery.startDiscovery { host ->
                if (host.hostId == pairedHost.hostId && host.ip != pairedHost.ip && pairedHost.ip != "127.0.0.1") {
                    securityStore.updateHostIpAndPort(host.ip, host.port)
                    client.connect()
                }
            }
        }
    }

    private fun observeCommandEvents() {
        applicationScope.launch {
            client.commandEvents.collectLatest { record ->
                // Update persistent history
                val history = securityStore.getHistory().toMutableList()
                history.removeAll { it.requestId == record.requestId }
                history.add(0, record)
                securityStore.saveHistory(history)

                if (securityStore.isNotificationsEnabled()) {
                    showCommandNotification(record)
                }
            }
        }
    }

    private fun showCommandNotification(record: CommandRecord) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (record.status == "COMPLETED") "Command Executed" else "Command ${record.status}"
        val text = "${record.rawCommand}: ${record.message}"

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(record.requestId.hashCode(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ALERTS,
                "Command Execution Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Linux command completion and errors"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(alertsChannel)
        }
    }
}
