package kotlinx.io.tests

import kotlinx.io.core.*
import kotlin.test.*
import org.khronos.webgl.*

class IoBufferNativeTest {
    private val buffer = IoBuffer.Pool.borrow()

    @AfterTest
    fun destroy() {
        buffer.release(IoBuffer.Pool)
    }

    @Test
    fun testReadDirectOnEmpty() {
        var invoked = false
        buffer.readDirect { ptr ->
            invoked = true
            0
        }
        assertTrue(invoked)
    }

    @Test
    fun testReadDirectNegativeResult() {
        var invoked = false
        assertFails {
            buffer.readDirect { ptr ->
                -1
            }
        }
    }

    @Test
    fun testReadDirectTooManyBytesResult() {
        var invoked = false
        assertFails {
            buffer.readDirect { ptr ->
                1
            }
        }
    }

    @Test
    fun testReadDirect() {
        var result = 0
        buffer.writeByte(7)
        buffer.writeByte(8)
        buffer.readDirect { ptr ->
            result = ptr.getInt8(0).toInt()
            1
        }
        assertEquals(7, result)
        assertEquals(8, buffer.readByte().toInt())
    }

    @Test
    fun testReadDirectWithEndGap() {
        var result = 0
        buffer.reserveEndGap(8)
        buffer.writeByte(9)
        buffer.writeByte(10)
        buffer.readDirect { ptr ->
            result = ptr.getInt8(0).toInt()
            1
        }
        assertEquals(9, result)
        assertEquals(10, buffer.readByte().toInt())
    }

    @Test
    fun testReadDirectWithStartGap() {
        var result = 0
        buffer.reserveStartGap(8)
        buffer.writeByte(11)
        buffer.writeByte(12)
        buffer.readDirect { ptr ->
            result = ptr.getInt8(0).toInt()
            1
        }
        assertEquals(11, result)
        assertEquals(12, buffer.readByte().toInt())
    }

    @Test
    fun testReadDirectAtEnd() {
        while (buffer.writeRemaining > 0) {
            buffer.writeByte(1)
        }

        buffer.readDirect { ptr ->
            buffer.readRemaining
        }

        assertEquals(0, buffer.readRemaining)
        buffer.readDirect { 0 }
    }

    @Test
    fun testWriteDirect() {
        buffer.writeDirect { ptr ->
            ptr.setInt8(0, 7)
            ptr.setInt8(1, 8)
            2
        }

        assertEquals(2, buffer.readRemaining, "remaining")
        assertEquals(7, buffer.readByte().toInt(), "first byte")
        assertEquals(8, buffer.readByte().toInt(), "second byte")
    }

    @Test
    fun testWriteDirectWithReserve() {
        buffer.reserveEndGap(8)

        buffer.writeDirect { ptr ->
            ptr.setInt8(0, 5)
            ptr.setInt8(1, 6)
            2
        }

        assertEquals(2, buffer.readRemaining, "remaining")
        assertEquals(5, buffer.readByte().toInt(), "first byte")
        assertEquals(6, buffer.readByte().toInt(), "second byte")
    }

    @Test
    fun testWriteDirectWithReservedStart() {
        buffer.reserveStartGap(8)

        buffer.writeDirect { ptr ->
            ptr.setInt8(0, 3)
            ptr.setInt8(1, 4)
            2
        }

        assertEquals(2, buffer.readRemaining, "remaining")
        assertEquals(3, buffer.readByte().toInt(), "first byte")
        assertEquals(4, buffer.readByte().toInt(), "second byte")
    }

    @Test
    fun testCombinedReadAndWrite() {
        buffer.reserveStartGap(4)
        buffer.reserveEndGap(1)

        buffer.writeByte(2)
        var rc = buffer.writeDirect { view ->
            assertTrue { view.byteLength > 0 }
            view.setInt8(0, 3)
            1
        }
        assertEquals(1,  rc)

        var value = 0
        rc = buffer.readDirect { view  ->
            assertEquals(2, view.byteLength)
            value = view.getInt8(0).toInt()
            1
        }
        assertEquals(1, rc)

        assertEquals(2, value)
        rc = buffer.writeDirect { view ->
            view.setInt8(0, 4)
            1
        }
        assertEquals(1, rc)

        rc = buffer.readDirect { view ->
            assertEquals(2, view.byteLength)
            assertEquals(3, view.getInt8(0))
            assertEquals(4, view.getInt8(1))
            2
        }
        assertEquals(2, rc)

        rc = buffer.writeDirect { 0 }
        assertEquals(0, rc)
        rc = buffer.readDirect { 0 }
        assertEquals(0, rc)
    }

    @Test
    fun testWriteDirectOnFull() {
        val size = buffer.capacity
        buffer.writeDirect { size }
        assertEquals(size, buffer.readRemaining)
        assertEquals(0, buffer.writeRemaining)
        buffer.writeDirect { 0 }
    }
}
