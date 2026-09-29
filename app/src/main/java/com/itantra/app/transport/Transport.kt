package com.itantra.app.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Listening : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

interface Transport {
    val incoming: Flow<MessagePayload>
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect()
    suspend fun connectTo(address: String)
    suspend fun disconnect()
    suspend fun send(payload: MessagePayload)
}
