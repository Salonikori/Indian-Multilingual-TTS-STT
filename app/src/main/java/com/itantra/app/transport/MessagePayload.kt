package com.itantra.app.transport

import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

enum class MessageType { SPEECH, ALERT, ACK, PING }

data class MessagePayload(
    val version: Int = CURRENT_VERSION,
    val type: MessageType,
    val messageId: String,
    val seq: Long,
    val senderId: String,
    val langCode: String? = null,
    val sentAtEpochMs: Long,
    val text: String? = null,
    val ackForMessageId: String? = null
) {
    fun validate() {
        require(version == CURRENT_VERSION) { "Unsupported protocol version: $version" }
        require(messageId.isNotBlank() && messageId.length <= 64) { "Invalid messageId" }
        require(seq >= 0) { "seq must be non-negative" }
        require(senderId.isNotBlank() && senderId.length <= 64) { "Invalid senderId" }
        require(sentAtEpochMs >= 0) { "Invalid timestamp" }
        if (type == MessageType.SPEECH || type == MessageType.ALERT) {
            require(!text.isNullOrBlank()) { "Speech/alert text is required" }
            require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) {
                "Text exceeds $MAX_TEXT_BYTES UTF-8 bytes"
            }
            require(!langCode.isNullOrBlank() && langCode.length <= 16) { "langCode is required" }
        }
        if (type == MessageType.ACK) require(!ackForMessageId.isNullOrBlank()) { "ACK must reference a messageId" }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 4096
        const val MAX_TEXT_BYTES = 2048
    }
}

object PayloadSerializer {
    fun encode(payload: MessagePayload): ByteArray {
        payload.validate()
        val obj = JSONObject()
            .put("version", payload.version).put("type", payload.type.name)
            .put("messageId", payload.messageId).put("seq", payload.seq)
            .put("senderId", payload.senderId).put("sentAtEpochMs", payload.sentAtEpochMs)
        payload.langCode?.let { obj.put("langCode", it) }
        payload.text?.let { obj.put("text", it) }
        payload.ackForMessageId?.let { obj.put("ackForMessageId", it) }
        val bytes = obj.toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MessagePayload.MAX_PAYLOAD_BYTES) { "Payload too large" }
        return bytes
    }

    fun decode(bytes: ByteArray): MessagePayload {
        require(bytes.isNotEmpty()) { "Empty payload" }
        require(bytes.size <= MessagePayload.MAX_PAYLOAD_BYTES) { "Payload too large" }
        try {
            val obj = JSONObject(String(bytes, StandardCharsets.UTF_8))
            val payload = MessagePayload(
                version = obj.getInt("version"),
                type = MessageType.valueOf(obj.getString("type")),
                messageId = obj.getString("messageId"), seq = obj.getLong("seq"),
                senderId = obj.getString("senderId"),
                langCode = obj.optString("langCode").takeIf { it.isNotBlank() && it != "null" },
                sentAtEpochMs = obj.getLong("sentAtEpochMs"),
                text = obj.optString("text").takeIf { it.isNotBlank() && it != "null" },
                ackForMessageId = obj.optString("ackForMessageId").takeIf { it.isNotBlank() && it != "null" }
            )
            payload.validate()
            return payload
        } catch (e: JSONException) {
            throw IllegalArgumentException("Malformed payload JSON", e)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed payload", e)
        }
    }
}
