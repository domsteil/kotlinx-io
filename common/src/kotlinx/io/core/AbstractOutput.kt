package kotlinx.io.core

import kotlinx.io.bits.*
import kotlinx.io.core.internal.*
import kotlinx.io.errors.*
import kotlinx.io.pool.*

/**
 * The default [Output] implementation.
 * @see flush
 * @see closeDestination
 */
@ExperimentalIoApi
abstract class AbstractOutput
internal constructor(
    private val headerSizeHint: Int,
    protected val pool: ObjectPool<ChunkBuffer>
) : Appendable, Output {
    constructor(pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool) : this(0, pool)

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    @Deprecated("Use ChunkBuffer's pool instead", level = DeprecationLevel.ERROR)
    constructor(pool: ObjectPool<IoBuffer>) : this(pool as ObjectPool<ChunkBuffer>)

    /**
     * An implementation should write the whole [buffer] to the destination. It should never capture the [buffer] instance
     * longer than this method execution since it will be disposed after return.
     */
    protected abstract fun flush(buffer: Buffer)

    /**
     * An implementation should only close the destination.
     */
    protected abstract fun closeDestination()

    private var _head: ChunkBuffer? = null
    private var _tail: ChunkBuffer? = null

    @PublishedApi
    internal val head: ChunkBuffer
        get() = _head ?: ChunkBuffer.Empty

    @PublishedApi
    internal var tail: ChunkBuffer
        get() {
            val _tail = _tail
            if (_tail != null) {
                if (_tail.commitWrittenUntilIndex(tailPosition)) {
                    return _tail
                }
            }
            return appendNewBuffer()
        }
        @Suppress("")
        set(_) {
            TODO_ERROR()
        }

    @Deprecated("Will be removed. Override flush(buffer) properly.", level = DeprecationLevel.ERROR)
    protected var currentTail: ChunkBuffer
        get() = this.tail
        set(newValue) {
            last(newValue)
        }

    internal var tailMemory: Memory = Memory.Empty
    internal var tailPosition = 0
    internal var tailEndExclusive = 0
        private set

    private var tailInitialPosition = 0
    private var chainedSize: Int = 0

    internal inline val tailRemaining: Int get() = tailEndExclusive - tailPosition
    internal inline val tailWritten: Int get() = tailPosition - tailInitialPosition

    /**
     * Number of bytes currently buffered (pending).
     */
    protected final var _size: Int
        get() = chainedSize + (tailPosition - tailInitialPosition)
        @Deprecated("There is no need to update/reset this value anymore.")
        set(_) {
        }

    /**
     * Byte order (Endianness) to be used by future write functions calls on this builder instance. Doesn't affect any
     * previously written values. Note that [reset] doesn't change this value back to the default byte order.
     * @default [ByteOrder.BIG_ENDIAN]
     */
    @Deprecated(
        "This is no longer supported. All operations are big endian by default. Use readXXXLittleEndian " +
            "to read primitives in little endian",
        level = DeprecationLevel.ERROR
    )
    final override var byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
        set(value) {
            field = value
            if (value != ByteOrder.BIG_ENDIAN) {
                throw IllegalArgumentException(
                    "Only BIG_ENDIAN is supported. Use corresponding functions to read/write" +
                        "in the little endian"
                )
            }
        }

    final override fun flush() {
        flushChain()
    }

    private fun flushChain() {
        val oldTail = stealAll() ?: return

        try {
            oldTail.forEachChunk { chunk ->
                flush(chunk)
            }
        } finally {
            oldTail.releaseAll(pool)
        }
    }

    /**
     * Detach all chunks and cleanup all internal state so builder could be reusable again
     * @return a chain of buffer views or `null` of it is empty
     */
    internal fun stealAll(): ChunkBuffer? {
        val head = this._head ?: return null

        _tail?.commitWrittenUntilIndex(tailPosition)

        this._head = null
        this._tail = null
        tailPosition = 0
        tailEndExclusive = 0
        tailInitialPosition = 0
        chainedSize = 0
        tailMemory = Memory.Empty

        return head
    }

    internal final fun last(buffer: ChunkBuffer) {
        check(buffer.next == null) { "It should be a single buffer chunk." }

        val _tail = _tail
        if (_tail == null) {
            _head = buffer
            chainedSize = 0
        } else {
            _tail.next = buffer
            val tailPosition = tailPosition
            _tail.commitWrittenUntilIndex(tailPosition)
            chainedSize += tailPosition - tailInitialPosition
        }

        this._tail = buffer
        tailMemory = buffer.memory
        tailPosition = buffer.writePosition
        tailInitialPosition = buffer.readPosition
        tailEndExclusive = buffer.limit
    }

    final override fun writeByte(v: Byte) {
        val index = tailPosition
        if (index < tailEndExclusive) {
            tailPosition = index + 1
            tailMemory[index] = v
            return
        }

        return writeByteFallback(v)
    }

    private fun writeByteFallback(v: Byte) {
        appendNewBuffer().writeByte(1)
        tailPosition++
    }

    /**
     * Should flush and close the destination
     */
    final override fun close() {
        try {
            flush()
        } finally {
            closeDestination() // TODO check what should be done here
        }
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFully(src: ByteArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeLong(v: Long) {
        (this as Output).writeLong(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeInt(v: Int) {
        (this as Output).writeInt(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeShort(v: Short) {
        (this as Output).writeShort(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeDouble(v: Double) {
        (this as Output).writeDouble(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    final override fun writeFloat(v: Float) {
        (this as Output).writeFloat(v)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: ShortArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: IntArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: LongArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: FloatArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: DoubleArray, offset: Int, length: Int) {
        (this as Output).writeFully(src, offset, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun writeFully(src: IoBuffer, length: Int) {
        writeFully(src as Buffer, length)
    }

    fun writeFully(src: Buffer, length: Int) {
        (this as Output).writeFully(src, length)
    }

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    override fun fill(n: Long, v: Byte) {
        (this as Output).fill(n, v)
    }

    /**
     * Append single UTF-8 character
     */
    override fun append(c: Char): AbstractOutput {
        write(3) {
            it.putUtf8Char(c.toInt())
        }
        return this
    }

    override fun append(csq: CharSequence?): AbstractOutput {
        if (csq == null) {
            appendChars("null", 0, 4)
        } else {
            appendChars(csq, 0, csq.length)
        }
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): AbstractOutput {
        if (csq == null) {
            return append("null", start, end)
        }

        appendChars(csq, start, end)

        return this
    }

    open fun writePacket(p: ByteReadPacket) {
        while (true) {
            val buffer = p.steal() ?: break
            last(buffer)
        }
    }

    /**
     * Write exact [n] bytes from packet to the builder
     */
    fun writePacket(p: ByteReadPacket, n: Int) {
        var remaining = n

        while (remaining > 0) {
            val headRemaining = p.headRemaining
            if (headRemaining <= remaining) {
                remaining -= headRemaining
                last(p.steal() ?: throw EOFException("Unexpected end of packet"))
            } else {
                p.read { view ->
                    writeFully(view, remaining)
                }
                break
            }
        }
    }

    /**
     * Write exact [n] bytes from packet to the builder
     */
    fun writePacket(p: ByteReadPacket, n: Long) {
        var remaining = n

        while (remaining > 0L) {
            val headRemaining = p.headRemaining.toLong()
            if (headRemaining <= remaining) {
                remaining -= headRemaining
                last(p.steal() ?: throw EOFException("Unexpected end of packet"))
            } else {
                p.read { view ->
                    writeFully(view, remaining.toInt())
                }
                break
            }
        }
    }

    override fun append(csq: CharArray, start: Int, end: Int): Appendable {
        appendChars(csq, start, end)
        return this
    }

    private fun appendChars(csq: CharSequence, start: Int, end: Int): Int {
        var idx = start
        if (idx >= end) return idx
        idx = tail.appendChars(csq, idx, end)

        while (idx < end) {
            idx = appendNewBuffer().appendChars(csq, idx, end)
        }

        commitSize()
        return idx
    }

    private fun appendChars(csq: CharArray, start: Int, end: Int): Int {
        var idx = start
        if (idx >= end) return idx
        idx = tail.appendChars(csq, idx, end)

        while (idx < end) {
            idx = appendNewBuffer().appendChars(csq, idx, end)
        }

        commitSize()
        return idx
    }

    fun writeStringUtf8(s: String) {
        append(s, 0, s.length)
    }

    fun writeStringUtf8(cs: CharSequence) {
        append(cs, 0, cs.length)
    }

//    fun writeStringUtf8(cb: CharBuffer) {
//        append(cb, 0, cb.remaining())
//    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Buffer.putUtf8Char(v: Int) = when {
        v in 1..0x7f -> {
            writeByte(v.toByte())
            1
        }
        v > 0x7ff -> {
            writeExact(3, "3 bytes character") { memory, offset ->
                memory[offset] = (0xe0 or ((v shr 12) and 0x0f)).toByte()
                memory[offset + 1] = (0x80 or ((v shr 6) and 0x3f)).toByte()
                memory[offset + 2] = (0x80 or (v and 0x3f)).toByte()
            }
            3
        }
        else -> {
            writeExact(2, "2 bytes character") { memory, offset ->
                memory[offset] = (0xc0 or ((v shr 6) and 0x1f)).toByte()
                memory[offset + 1] = (0x80 or (v and 0x3f)).toByte()
            }
            2
        }
    }

    /**
     * Release any resources that the builder holds. Builder shouldn't be used after release
     */
    final fun release() {
        close()
    }

    @DangerousInternalIoApi
    fun prepareWriteHead(n: Int): ChunkBuffer {
        return tail.takeIf { it.writeRemaining >= n } ?: appendNewBuffer()
    }

    @DangerousInternalIoApi
    fun afterHeadWrite() {
        _size = -1
    }

    /**
     * Discard all written bytes and prepare to build another packet.
     */
    fun reset() {
        release()
    }

    @PublishedApi
    internal inline fun write(size: Int, block: (Buffer) -> Int) {
        val buffer = prepareWriteHead(size)
        addSize(block(buffer))
    }

    @Deprecated("Not sure about it.")
    private fun commitSize() {
        tailPosition = _tail?.writePosition ?: 0
    }

    @PublishedApi
    @Deprecated("There is no need to do that anymore.")
    internal fun addSize(n: Int) {
        check(n >= 0) { "It should be non-negative size increment: $n" }
        check(n <= tailRemaining) { "Unable to mark more bytes than available: $n > $tailRemaining" }

        // For binary compatibility we need to update pointers
        tailPosition += n
    }

    @Suppress("DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    internal open fun last(buffer: IoBuffer) {
        last(buffer as ChunkBuffer)
    }

    @PublishedApi
    internal fun appendNewBuffer(): ChunkBuffer {
        val new = pool.borrow()
        new.reserveEndGap(Buffer.ReservedSize)

        last(new)

        return new
    }
}
