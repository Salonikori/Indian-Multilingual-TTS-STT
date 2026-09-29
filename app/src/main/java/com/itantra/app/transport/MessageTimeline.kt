package com.itantra.app.transport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TimelineItem(val payload: MessagePayload, val sentByMe: Boolean, val delivered: Boolean,
                        val ackRttMillis: Long? = null)

@Composable
fun MessageTimeline(items: List<TimelineItem>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.payload.messageId }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (item.sentByMe) "Sent" else "Received", style = MaterialTheme.typography.labelMedium)
                    Text(item.payload.text ?: "[${item.payload.type}]")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.payload.sentAtEpochMs)),
                            style = MaterialTheme.typography.bodySmall)
                        if (item.sentByMe) Text(if (item.delivered) "Delivered ✓" else "Pending")
                        item.ackRttMillis?.let { Text("ACK RTT ${it} ms; one-way estimate ${it / 2.0} ms") }
                    }
                }
            }
        }
    }
}
