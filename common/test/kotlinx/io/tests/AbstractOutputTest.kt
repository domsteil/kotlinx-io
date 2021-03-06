package kotlinx.io.tests

import kotlinx.io.core.*
import kotlin.test.*

class AbstractOutputTest {
    @Test
    fun smokeTest() {
        val builder = BytePacketBuilder()

        val output = object : AbstractOutput() {
            override fun closeDestination() {
            }

            override fun flush(buffer: IoBuffer) {
                builder.writeFully(buffer)
            }
        }

        output.use {
            it.append("test")
        }

        val pkt = builder.build().readText()
        assertEquals("test", pkt)
    }

    @Test
    fun testCopy() {
        val result = BytePacketBuilder()

        val output = object : AbstractOutput() {
            override fun closeDestination() {
            }

            override fun flush(buffer: IoBuffer) {
                result.writeFully(buffer)
            }
        }

        val fromHead = IoBuffer.Pool.borrow()
        var current = fromHead
        repeat(3) {
            current.append("test $it. ")
            val next = IoBuffer.Pool.borrow()
            current.next = next
            current = next
        }

        current.append("end.")

        val from = ByteReadPacket(fromHead, IoBuffer.Pool)

        from.copyTo(output)
        output.flush()

        assertEquals("test 0. test 1. test 2. end.", result.build().readText())
    }
}
