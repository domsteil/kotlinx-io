package kotlinx.io.core

import kotlinx.io.bits.*
import kotlinx.io.core.internal.*
import kotlinx.io.errors.*
import kotlinx.io.pool.*

expect val PACKET_MAX_COPY_SIZE: Int

/**
 * Build a byte packet in [block] lambda. Creates a temporary builder and releases it in case of failure
 */
inline fun buildPacket(headerSizeHint: Int = 0, block: BytePacketBuilder.() -> Unit): ByteReadPacket {
    val builder = BytePacketBuilder(headerSizeHint)
    try {
        block(builder)
        return builder.build()
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

expect fun BytePacketBuilder(headerSizeHint: Int = 0): BytePacketBuilder

/**
 * A builder that provides ability to build byte packets with no knowledge of it's size.
 * Unlike Java's ByteArrayOutputStream it doesn't copy the whole content every time it's internal buffer overflows
 * but chunks buffers instead. Packet building via [build] function is O(1) operation and only does instantiate
 * a new [ByteReadPacket]. Once a byte packet has been built via [build] function call, the builder could be
 * reused again. You also can discard all written bytes via [reset] or [release]. Please note that an instance of
 * builder need to be terminated either via [build] function invocation or via [release] call otherwise it will
 * cause byte buffer leak so that may have performance impact.
 *
 * Byte packet builder is also an [Appendable] so it does append UTF-8 characters to a packet
 *
 * ```
 * buildPacket {
 *     listOf(1,2,3).joinTo(this, separator = ",")
 * }
 * ```
 */
class BytePacketBuilder(private var headerSizeHint: Int, pool: ObjectPool<ChunkBuffer>) :
    @Suppress("DEPRECATION_ERROR") BytePacketBuilderPlatformBase(pool) {
    init {
        require(headerSizeHint >= 0) { "shouldn't be negative: headerSizeHint = $headerSizeHint" }
    }

    /**
     * Number of bytes written to the builder
     */
    val size: Int
        get() {
            val size = _size
            if (size == -1) {
                _size = head.remainingAll().toInt()
                return _size
            }
            return size
        }

    val isEmpty: Boolean
        get() {
            val _size = _size
            return when {
                _size > 0 -> false
                _size == 0 -> true
                head.canRead() -> false
                size == 0 -> true
                else -> false
            }
        }

    val isNotEmpty: Boolean
        get() {
            val _size = _size
            return when {
                _size > 0 -> true
                _size == 0 -> false
                head.canRead() -> true
                size > 0 -> true
                else -> false
            }
        }

    @PublishedApi
    internal var head: ChunkBuffer = ChunkBuffer.Empty

    override fun append(c: Char): BytePacketBuilder {
        return super.append(c) as BytePacketBuilder
    }

    override fun append(csq: CharSequence?): BytePacketBuilder {
        return super.append(csq) as BytePacketBuilder
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): BytePacketBuilder {
        return super.append(csq, start, end) as BytePacketBuilder
    }

    /**
     * Release any resources that the builder holds. Builder shouldn't be used after release
     */
    override fun release() {
        val head = this.head
        val empty = ChunkBuffer.Empty

        if (head !== empty) {
            this.head = empty
            this.tail = null
            head.releaseAll(pool)
            _size = 0
        }
    }

    override fun flush() {
    }

    override fun close() {
        release()
    }

    /**
     * Creates a temporary packet view of the packet being build without discarding any bytes from the builder.
     * This is similar to `build().copy()` except that the builder keeps already written bytes untouched.
     * A temporary view packet is passed as argument to [block] function and it shouldn't leak outside of this block
     * otherwise an unexpected behaviour may occur.
     */
    fun <R> preview(block: (tmp: ByteReadPacket) -> R): R {
        val head = head.copyAll()
        val pool = if (head === ChunkBuffer.Empty) ChunkBuffer.EmptyPool else pool
        val packet = ByteReadPacket(head, pool)

        return try {
            block(packet)
        } finally {
            packet.release()
        }
    }

    /**
     * Builds byte packet instance and resets builder's state to be able to build another one packet if needed
     */
    fun build(): ByteReadPacket {
        val size = size
        val head = stealAll()

        return when (head) {
            null -> ByteReadPacket.Empty
            else -> ByteReadPacket(head, size.toLong(), pool)
        }
    }

    /**
     * Detach all chunks and cleanup all internal state so builder could be reusable again
     * @return a chain of buffer views or `null` of it is empty
     */
    internal fun stealAll(): ChunkBuffer? {
        val head = this.head
        val empty = ChunkBuffer.Empty

        this.head = empty
        this.tail = null
        this._size = 0

        return if (head === empty) null else head
    }

    internal fun afterBytesStolen() {
        val head = head
        check(head.next == null)
        _size = 0
        head.resetForWrite()
        head.reserveStartGap(headerSizeHint)
        head.reserveEndGap(Buffer.ReservedSize)
    }

    /**
     * Writes another packet to the end. Please note that the instance [p] gets consumed so you don't need to release it
     */
    override fun writePacket(p: ByteReadPacket) {
        val foreignStolen = p.stealAll()
        if (foreignStolen == null) {
            p.release()
            return
        }

        val tail = tail
        if (tail == null) {
            head = foreignStolen
            this.tail = foreignStolen.findTail()
            _size = foreignStolen.remainingAll().toInt()
            return
        }

        writePacketSlow(tail, foreignStolen, p)
    }

    private fun writePacketSlow(tail: ChunkBuffer, foreignStolen: ChunkBuffer, p: ByteReadPacket) {
        val lastSize = tail.readRemaining
        val nextSize = foreignStolen.readRemaining

        val maxCopySize = PACKET_MAX_COPY_SIZE
        val appendSize = if (nextSize < maxCopySize && nextSize <= (tail.endGap + tail.writeRemaining)) {
            nextSize
        } else -1

        val prependSize =
            if (lastSize < maxCopySize && lastSize <= foreignStolen.startGap && foreignStolen.isExclusivelyOwned()) {
                lastSize
            } else -1

        if (appendSize == -1 && prependSize == -1) {
            // simply enqueue
            tail.next = foreignStolen
            this.tail = foreignStolen.findTail()
            _size = head.remainingAll().toInt()
        } else if (prependSize == -1 || appendSize <= prependSize) {
            // do append
            tail.writeBufferAppend(foreignStolen, tail.writeRemaining + tail.endGap)
            tail.next = foreignStolen.next
            this.tail = foreignStolen.findTail().takeUnless { it === foreignStolen } ?: tail
            foreignStolen.release(p.pool)
            _size = head.remainingAll().toInt()
        } else if (appendSize == -1 || prependSize < appendSize) {
            writePacketSlowPrepend(foreignStolen, tail)
        } else {
            throw IllegalStateException("prep = $prependSize, app = $appendSize")
        }
    }

    private fun writePacketSlowPrepend(foreignStolen: ChunkBuffer, tail: ChunkBuffer) {
        // do prepend
        foreignStolen.writeBufferPrepend(tail)

        if (head === tail) {
            head = foreignStolen
        } else {
            var pre = head
            while (true) {
                val next = pre.next!!
                if (next === tail) break
                pre = next
            }

            pre.next = foreignStolen
        }
        tail.release(pool)

        this.tail = foreignStolen.findTail()
        _size = head.remainingAll().toInt()
    }
}

@DangerousInternalIoApi
@Deprecated("Will be removed in future releases.", level = DeprecationLevel.ERROR)
@Suppress("DEPRECATION_ERROR")
abstract class BytePacketBuilderPlatformBase
internal constructor(pool: ObjectPool<ChunkBuffer>) : BytePacketBuilderBase(pool)

@DangerousInternalIoApi
@Deprecated("Will be removed in future releases", level = DeprecationLevel.ERROR)
@Suppress("DEPRECATION_ERROR")
abstract class BytePacketBuilderBase
internal constructor(pool: ObjectPool<ChunkBuffer>) : AbstractOutput(pool)

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

    private var head: ChunkBuffer? = null
    private var _tail: ChunkBuffer? = null

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
        flushChain(null)
    }

    private fun flushChain(newTail: ChunkBuffer?) {
        val oldTail = tail
        tail = newTail

        if (oldTail == null) return

        try {
            oldTail.forEachChunk { chunk ->
                flush(chunk)
            }
        } finally {
            oldTail.releaseAll(pool)
        }
    }

    internal final fun last(buffer: ChunkBuffer) {
        check(buffer.next == null) { "It should be a single buffer chunk." }

        val _tail = _tail
        if (_tail == null) {
            head = buffer
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
    override fun append(c: Char): BytePacketBuilderBase {
        write(3) {
            it.putUtf8Char(c.toInt())
        }
        return this
    }

    override fun append(csq: CharSequence?): BytePacketBuilderBase {
        if (csq == null) {
            appendChars("null", 0, 4)
        } else {
            appendChars(csq, 0, csq.length)
        }
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): BytePacketBuilderBase {
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
        val tail = tail ?: appendNewBuffer()
        idx = tail.appendChars(csq, idx, end)

        while (idx < end) {
            idx = appendNewBuffer().appendChars(csq, idx, end)
        }

        this._size = -1
        return idx
    }

    private fun appendChars(csq: CharArray, start: Int, end: Int): Int {
        var idx = start
        if (idx >= end) return idx
        val tail = tail ?: appendNewBuffer()
        idx = tail.appendChars(csq, idx, end)

        while (idx < end) {
            idx = appendNewBuffer().appendChars(csq, idx, end)
        }

        this._size = -1
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

private inline fun <T> T.takeUnless(predicate: (T) -> Boolean): T? {
    return if (!predicate(this)) this else null
}


