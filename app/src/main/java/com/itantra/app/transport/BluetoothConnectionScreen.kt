package com.itantra.app.transport

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BluetoothConnectionScreen(
    pairedDevices: List<BluetoothDevice>,
    state: ConnectionState,
    onHost: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bluetooth text link", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onHost) { Text("Host") }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }
        Text("Connection: ${when (state) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Listening -> "Hosting — waiting for peer"
            ConnectionState.Connecting -> "Connecting…"
            is ConnectionState.Connected -> "Connected to ${state.deviceName}"
            is ConnectionState.Error -> "Error: ${state.message}"
        }}")
        Text("Paired devices")
        LazyColumn {
            items(pairedDevices, key = { it.address }) { device ->
                ListItem(
                    headlineContent = { Text(device.name ?: "Unnamed device") },
                    supportingContent = { Text(device.address) },
                    trailingContent = { TextButton(onClick = { onConnect(device.address) }) { Text("Connect") } }
                )
            }
        }
        Text("Pair in Android Settings first. Grant Nearby devices/Bluetooth permission and ensure Bluetooth is on.")
    }
}
