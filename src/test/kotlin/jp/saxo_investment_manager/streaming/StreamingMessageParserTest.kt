package jp.saxo_investment_manager.streaming

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class StreamingMessageParserTest {

    /** Builds one Saxo streaming frame with the given fields. */
    private fun frame(messageId: Long, referenceId: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(8) { out.write(((messageId shr (8 * it)) and 0xFF).toInt()) } // messageId LE
        out.write(0); out.write(0) // reserved
        val ref = referenceId.toByteArray(Charsets.US_ASCII)
        out.write(ref.size)
        out.write(ref)
        out.write(0) // payload format = JSON
        repeat(4) { out.write((payload.size shr (8 * it)) and 0xFF) } // payload size LE
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `parses a single JSON message`() {
        val payload = """{"Quote":{"Mid":1.5}}""".toByteArray()
        val result = StreamingMessageParser.parse(frame(42, "px1", payload))

        assertEquals(1, result.messages.size)
        val msg = result.messages.single()
        assertEquals(42L, msg.messageId)
        assertEquals("px1", msg.referenceId)
        assertEquals(0, msg.payloadFormat)
        assertEquals("""{"Quote":{"Mid":1.5}}""", String(msg.payload))
        assertEquals(result.consumed, frame(42, "px1", payload).size)
    }

    @Test
    fun `parses multiple concatenated messages in one buffer`() {
        val buf = frame(1, "_heartbeat", "{}".toByteArray()) + frame(2, "pxA", """{"x":1}""".toByteArray())
        val result = StreamingMessageParser.parse(buf)

        assertEquals(2, result.messages.size)
        assertEquals("_heartbeat", result.messages[0].referenceId)
        assertEquals("pxA", result.messages[1].referenceId)
        assertEquals(buf.size, result.consumed)
    }

    @Test
    fun `leaves an incomplete trailing message unconsumed`() {
        val full = frame(7, "pxB", """{"a":true}""".toByteArray())
        val truncated = full.copyOfRange(0, full.size - 3) // drop 3 payload bytes

        val result = StreamingMessageParser.parse(truncated)

        assertEquals(0, result.messages.size)
        assertEquals(0, result.consumed) // nothing consumed until the whole message arrives
    }
}
