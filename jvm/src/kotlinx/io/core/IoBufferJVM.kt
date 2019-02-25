@file:Suppress("RedundantModalityModifier", "DEPRECATION", "DEPRECATION_ERROR")

package kotlinx.io.core

import kotlinx.io.bits.*
import kotlinx.io.core.internal.*
import kotlinx.io.internal.jvm.*
import kotlinx.io.pool.*
import kotlinx.io.utils.*
import java.nio.*
import java.nio.charset.*
import kotlinx.io.core.internal.require
import kotlin.contracts.*

/**
 * A read-write facade to actual buffer of fixed size. Multiple views could share the same actual buffer.
 */
@Deprecated("")
actual class IoBuffer private constructor(
    private var content: ByteBuffer,
    origin: ChunkBuffer?
) : Input, Output, ChunkBuffer(Memory.of(content), origin) {

    constructor(external: ByteBuffer) : this(external, null)

    @PublishedApi
    @Deprecated("")
    internal var readBuffer: ByteBuffer
        get() = memory.buffer.sliceSafe(readPosition, writePosition)
        @Deprecated("", level = DeprecationLevel.ERROR)
        set(_) {
            TODO()
        }

    @PublishedApi
    @Deprecated("")
    internal var writeBuffer: ByteBuffer
        get() = memory.buffer.sliceSafe(writePosition, limit)
        set(_) {
            TODO()
        }

    /**
     * User data: could be a session, connection or anything useful
     */
    @ExperimentalIoApi
    actual var attachment: Any? = null

    /**
     * @return `true` if there are available bytes to be read
     */
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun canRead(): Boolean = canRead()

    /**
     * @return `true` if there is free room to for write
     */
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun canWrite(): Boolean = canWrite()

    override val endOfInput: Boolean
        get() = !canRead()

    /**
     * read and write operations byte-order (endianness)
     */
    @Deprecated(
        "All read operations are big endian by default except functions with LittleEndian suffix",
        level = DeprecationLevel.ERROR
    )
    final override var byteOrder: ByteOrder
        get() = ByteOrder.of(readBuffer.order())
        set(value) {
            if (value != ByteOrder.BIG_ENDIAN) {
                throw UnsupportedOperationException("Only BIG_ENDIAN is supported")
            }
        }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeShort(v: Short) {
        writeShort(v)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeInt(v: Int) {
        writeInt(v)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeLong(v: Long) {
        writeLong(v)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFloat(v: Float) {
        writeFloat(v)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeDouble(v: Double) {
        writeDouble(v)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: ByteArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: ShortArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: IntArray, offset: Int, length: Int) {
        val bb = writeBuffer
        for (i in offset until offset + length) {
            bb.putInt(src[i])
        }
        afterWrite()
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: LongArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: FloatArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: IoBuffer, length: Int) {
        (this as Buffer).writeFully(src, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    override fun writeFully(bb: ByteBuffer) {
        (this as Buffer).writeFully(bb)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        (this as Buffer).append(csq, start, end)
        return this
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun append(csq: CharSequence?): Appendable {
        (this as Buffer).append(csq)
        return this
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun append(csq: CharArray, start: Int, end: Int): Appendable {
        val idx = appendChars(csq, start, end)

        if (idx != end) throw IllegalStateException("Not enough free space to append char sequence")
        return this
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    override fun append(c: Char): Appendable {
        (this as Buffer).append(c)
        return this
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun appendChars(csq: CharArray, start: Int, end: Int): Int {
        return (this as Buffer).appendChars(csq, start, end)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun appendChars(csq: CharSequence, start: Int, end: Int): Int {
        return (this as Buffer).appendChars(csq, start, end)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun fill(n: Long, v: Byte) {
        (this as Buffer).fill(n, v)
    }

    /**
     * Apply [block] function on a [ByteBuffer] of readable bytes.
     * The [block] function should return number of consumed bytes.
     * @return number of bytes consumed
     */
    inline fun readDirect(block: (ByteBuffer) -> Unit): Int {
        val readPosition = readPosition
        val writePosition = writePosition
        val bb = memory.buffer.duplicate()!!
        bb.limit(writePosition)
        bb.position(readPosition)

        block(bb)

        val delta = bb.position() - readPosition
        if (delta < 0) negativeShiftError(delta)
        if (bb.limit() != writePosition) limitChangeError()
        discard(delta)

        return delta
    }

    /**
     * Apply [block] function on a [ByteBuffer] of the free space.
     * The [block] function should return number of written bytes.
     * @return number of bytes written
     */
    inline fun writeDirect(size: Int, block: (ByteBuffer) -> Unit): Int {
        val rem = writeRemaining
        require(size <= rem) { "size $size is greater than buffer's remaining capacity $rem" }
        val buffer = memory.buffer.duplicate()!!
        val writePosition = writePosition
        val limit = limit
        buffer.limit(limit)
        buffer.position(writePosition)

        block(buffer)

        val delta = buffer.position() - writePosition
        if (delta < 0 || delta > rem) wrongBufferPositionChangeError(delta, size)

        commitWritten(delta)

        return delta
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readShort() = readShort()

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readInt() = readInt()

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readLong() = readLong()

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFloat() = readFloat()

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readDouble() = readDouble()

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ByteArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: IoBuffer, length: Int) {
        (this as Buffer).readFully(dst, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: IoBuffer, length: Int): Int {
        return (this as Buffer).readAvailable(dst, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ShortArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: IntArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: LongArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: DoubleArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: FloatArray, offset: Int, length: Int) {
        (this as Buffer).readFully(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: IntArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: LongArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int {
        return (this as Buffer).readAvailable(dst, offset, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readAvailable(dst: ByteBuffer, length: Int): Int {
        return (this as Buffer).readAvailable(dst, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun readFully(dst: ByteBuffer, length: Int) {
        (this as Buffer).readFully(dst, length)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun peekTo(buffer: IoBuffer): Int {
        return (this as Buffer).peekTo(buffer)
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    final override fun discard(n: Long): Long {
        require(n >= 0L) { "Negative discard quantity $n" }
        val size = minOf(readRemaining.toLong(), n).toInt()
        discard(size)
        return size.toLong()
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun discardExact(n: Int) {
        discard(n)
    }

    /**
     * Push back [n] bytes: only possible if there were at least [n] bytes read before this operation.
     */
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun pushBack(n: Int) {
        rewind(n)
    }

    /**
     * Reset read/write position to original's content pos/limit. May not work due to slicing.
     */
    @Deprecated("Unstable API. Could be changed or removed without notice.")
    fun resetFromContentToWrite(child: ByteBuffer) {
        writeBuffer.limit(child.limit())
        writeBuffer.position(child.position())
    }

    /**
     * @return `true` if and only if the are no buffer views that share the same actual buffer. This actually does
     * refcount and only work guaranteed if other views created/not created via [makeView] function.
     * One can instantiate multiple buffers with the same buffer and this function will return `true` in spite of
     * the fact that the buffer is actually shared.
     */
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun isExclusivelyOwned(): Boolean = referenceCount == 1

    /**
     * Creates a new view to the same actual buffer with independent read and write positions and gaps
     */
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun makeView(): IoBuffer {
        return duplicate()
    }

    override fun duplicate(): IoBuffer = (origin ?: this).let { newOrigin ->
        newOrigin.acquire()
        IoBuffer(memory.buffer, newOrigin).also { copy ->
            duplicateTo(copy)
        }
    }

    actual override fun close() {
        throw UnsupportedOperationException("close for buffer view is not supported")
    }

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    fun readText(decoder: CharsetDecoder, out: Appendable, lastBuffer: Boolean, max: Int = Int.MAX_VALUE): Int {
        return (this as Buffer).readText(decoder, out, lastBuffer, max)
    }

    actual final override fun flush() {
    }

    @PublishedApi
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun afterWrite() {
    }

    override fun toString(): String =
        "Buffer[readable = $readRemaining, writable = $writeRemaining, startGap = $startGap, endGap = $endGap]"

    actual companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [IoBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        actual val ReservedSize: Int get() = Buffer.ReservedSize

        private val DEFAULT_BUFFER_SIZE = getIOIntProperty("buffer.size", 4096)
        private val DEFAULT_BUFFER_POOL_SIZE = getIOIntProperty("buffer.pool.size", 100)
        private val DEFAULT_BUFFER_POOL_DIRECT = getIOIntProperty("buffer.pool.direct", 0)

        actual val Empty = IoBuffer(Buffer.Empty.memory.buffer, null)

        actual val Pool: ObjectPool<IoBuffer> = object : DefaultPool<IoBuffer>(DEFAULT_BUFFER_POOL_SIZE) {
            override fun produceInstance(): IoBuffer {
                val buffer = when (DEFAULT_BUFFER_POOL_DIRECT) {
                    0 -> ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                    else -> ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
                }
                return IoBuffer(buffer, null)
            }

            override fun disposeInstance(instance: IoBuffer) {
                instance.unlink()
            }

            override fun clearInstance(instance: IoBuffer): IoBuffer {
                return instance.apply {
                    unpark()
                    next = null
                    attachment = null
                }
            }

            override fun validateInstance(instance: IoBuffer) {
                require(instance.referenceCount == 0) { "Buffer is not yet released but tried to recycle" }
                require(instance.origin == null) { "Unable to recycle buffer view, only origin buffers are applicable" }
            }
        }

        actual val NoPool: ObjectPool<IoBuffer> = object : NoPoolImpl<IoBuffer>() {
            override fun borrow(): IoBuffer {
                val buffer = when (DEFAULT_BUFFER_POOL_DIRECT) {
                    0 -> ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                    else -> ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
                }
                return IoBuffer(buffer, null)
            }
        }

        actual val EmptyPool: ObjectPool<IoBuffer> = EmptyBufferPoolImpl
    }
}

fun Buffer.readFully(dst: ByteBuffer, length: Int) {
    readExact(length, "buffer content") { memory, offset ->
        val limit = dst.limit()
        try {
            dst.limit(dst.position() + length)
            memory.copyTo(dst, offset)
        } finally {
            dst.limit(limit)
        }
    }
}

fun Buffer.readAvailable(dst: ByteBuffer, length: Int = dst.remaining()): Int {
    if (!canRead()) return -1
    val size = minOf(readRemaining, length)
    readFully(dst, size)
    return size
}

inline fun Buffer.readDirect(block: (ByteBuffer) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return read { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).buffer)
    }
}

inline fun Buffer.writeDirect(block: (ByteBuffer) -> Int): Int {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return write { memory, start, endExclusive ->
        block(memory.slice(start, endExclusive - start).buffer)
    }
}
