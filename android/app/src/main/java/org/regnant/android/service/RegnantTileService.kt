package org.regnant.android.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.regnant.android.RegnantApp
import org.regnant.android.data.ConnectionStatus

@RequiresApi(Build.VERSION_CODES.N)
class RegnantTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = application as? RegnantApp ?: return
        val client = app.client

        scope.launch {
            if (client.connectionStatus.value == ConnectionStatus.CONNECTED) {
                // Lock workstation
                client.lockWorkstation()
            } else {
                // Connect
                client.connect()
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val app = application as? RegnantApp ?: return
        val client = app.client
        val tile = qsTile ?: return

        val isConnected = client.connectionStatus.value == ConnectionStatus.CONNECTED
        tile.state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isConnected) "Regnant: Ready" else "Regnant: Connect"
        tile.updateTile()
    }
}
