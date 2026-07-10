package jp.saxo_investment_manager.streaming

/**
 * A decoded Saxo streaming message.
 *
 * @param referenceId the subscription's reference id, or a control id such as `_heartbeat`.
 * @param payloadFormat 0 = JSON (UTF-8), 1 = Protobuf.
 */
data class StreamingMessage(
    val messageId: Long,
    val referenceId: String,
    val payloadFormat: Int,
    val payload: ByteArray,
)

/** [messages] parsed from the front of a buffer, and how many bytes were [consumed]. */
data class ParseResult(val messages: List<StreamingMessage>, val consumed: Int)

/**
 * Parses Saxo's binary streaming frame format. Each message is laid out as:
 *
 * ```
 * offset 0   : messageId        (8 bytes, little-endian uint64)
 * offset 8   : reserved         (2 bytes, ignored)
 * offset 10  : refIdSize        (1 byte)
 * offset 11  : referenceId      (refIdSize bytes, ASCII)
 * +          : payloadFormat    (1 byte: 0=JSON, 1=Protobuf)
 * +          : payloadSize      (4 bytes, little-endian uint32)
 * +          : payload          (payloadSize bytes)
 * ```
 *
 * A single WebSocket frame may contain several concatenated messages, and one message may span
 * multiple frames. [parse] therefore decodes as many *complete* messages as the buffer holds and
 * reports how many bytes it consumed, so the caller can retain the remainder and prepend the next
 * frame to it.
 */
object StreamingMessageParser {

    fun parse(bytes: ByteArray): ParseResult {
        val messages = mutableListOf<StreamingMessage>()
        var offset = 0
        while (true) {
            // Need at least the fixed header up to and including refIdSize.
            if (bytes.size - offset < 11) break
            val refIdSize = bytes[offset + 10].toInt() and 0xFF
            val payloadSizeOffset = offset + 11 + refIdSize + 1
            // Need the reference id, payload-format byte and the 4-byte payload size.
            if (bytes.size < payloadSizeOffset + 4) break
            val payloadSize = readIntLE(bytes, payloadSizeOffset)
            val payloadStart = payloadSizeOffset + 4
            if (bytes.size - payloadStart < payloadSize) break // payload not fully arrived yet

            val messageId = readLongLE(bytes, offset)
            val referenceId = String(bytes, offset + 11, refIdSize, Charsets.US_ASCII)
            val payloadFormat = bytes[offset + 11 + refIdSize].toInt() and 0xFF
            val payload = bytes.copyOfRange(payloadStart, payloadStart + payloadSize)

            messages.add(StreamingMessage(messageId, referenceId, payloadFormat, payload))
            offset = payloadStart + payloadSize
        }
        return ParseResult(messages, offset)
    }

    private fun readLongLE(b: ByteArray, o: Int): Long {
        var r = 0L
        for (i in 0..7) r = r or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        return r
    }

    private fun readIntLE(b: ByteArray, o: Int): Int {
        var r = 0
        for (i in 0..3) r = r or ((b[o + i].toInt() and 0xFF) shl (8 * i))
        return r
    }
}
