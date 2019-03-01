package kotlinx.io.core

import kotlinx.io.core.internal.*
import kotlinx.io.core.internal.require
import kotlinx.io.errors.*
import kotlinx.io.errors.incompatibleVersionError
import kotlinx.io.pool.*

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
     * Number of bytes written to the builder after the creation or the last reset.
     */
    val size: Int
        get() = _size

    /**
     * If no bytes were written or the builder has been reset.
     */
    val isEmpty: Boolean
        get() = _size == 0

    /**
     * If at least one byte was written after the creation or the last reset.
     */
    val isNotEmpty: Boolean
        get() = _size > 0

    @PublishedApi
    @Suppress("unused", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    internal fun getHead(): IoBuffer = incompatibleVersionError()

    @PublishedApi
    @Suppress("unused", "DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    internal fun setHead(newValue: IoBuffer) {
        incompatibleVersionError()
    }

    @PublishedApi
    internal val _pool: ObjectPool<ChunkBuffer>
        get() = pool

    /**
     * Does nothing for memory-backed output
     */
    final override fun closeDestination() {
    }

    /**
     * Does nothing for memory-backed output
     */
    final override fun flush(buffer: Buffer) {
    }

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
     * Creates a temporary packet view of the packet being build without discarding any bytes from the builder.
     * This is similar to `build().copy()` except that the builder keeps already written bytes untouched.
     * A temporary view packet is passed as argument to [block] function and it shouldn't leak outside of this block
     * otherwise an unexpected behaviour may occur.
     */
    @Suppress("unused")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun <R> preview(block: (tmp: ByteReadPacket) -> R): R {
        return preview(block)
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

        val tail = _tail
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
