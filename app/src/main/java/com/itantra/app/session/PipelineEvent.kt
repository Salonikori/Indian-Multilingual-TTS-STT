package com.itantra.app.session

/** Timestamped, monotonic benchmark event. Values are elapsedRealtimeNanos, not wall clock. */
data class PipelineEvent(
    val name: String,
    val elapsedRealtimeNanos: Long,
    val messageId: String? = null,
    val details: Map<String, String> = emptyMap()
)

fun interface PipelineEventSink {
    fun emit(event: PipelineEvent)
}
