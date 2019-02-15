@file:Suppress("NOTHING_TO_INLINE")

package kotlinx.io.core

import kotlinx.io.bits.*
import kotlinx.io.errors.EOFException

open class Buffer(val memory: Memory) {
    var readPosition = 0
        private set

    var writePosition = 0
        private set

    var startGap: Int = 0
        private set

    var limit: Int = memory.size32
        private set

    val capacity: Int = memory.size32

    inline val readRemaining: Int get() = writePosition - readPosition
    inline val writeRemaining: Int get() = limit - writePosition

    fun discard(count: Int = readRemaining) {
        val newReadPosition = readPosition + count
        if (count < 0 || newReadPosition > writePosition) {
            discardFailed(count, readRemaining)
        }
        readPosition = newReadPosition
    }

    internal fun commitWritten(count: Int) {
        val newWritePosition = writePosition + count
        if (count < 0 || newWritePosition > limit) {
            commitWrittenFailed(count, writeRemaining)
        }
        writePosition = newWritePosition
    }

    fun rewind(count: Int = readPosition) {
        val newReadPosition = readPosition - count
        if (newReadPosition < 0) {
            rewindFailed(count, readPosition)
        }
        readPosition = newReadPosition
    }

    fun reserveStartGap(startGap: Int) {
        require(startGap > 0)

        if (readPosition >= startGap) {
            this.startGap = startGap
            return
        }

        if (readPosition == writePosition) {
            if (startGap > limit) {
                startGapReservationFailedDueToLimit(startGap)
            }

            this.writePosition = startGap
            this.readPosition = startGap
            this.startGap = startGap
            return
        }

        startGapReservationFailed(startGap)
    }

    fun reserveEndGap(endGap: Int) {
        require(endGap > 0)

        val newLimit = capacity - endGap
        if (newLimit >= writePosition) {
            limit = newLimit
            return
        }

        if (newLimit < 0) {
            endGapReservationFailedDueToCapacity(endGap)
        }
        if (newLimit < startGap) {
            endGapReservationFailedDueToStartGap(endGap)
        }

        if (readPosition == writePosition) {
            limit = newLimit
            readPosition = newLimit
            writePosition = newLimit
            return
        }

        endGapReservationFailedDueToContent(endGap)
    }

    /**
     * Marks the whole buffer available for read and no for write
     */
    fun resetForRead() {
        startGap = 0
        readPosition = 0

        val capacity = capacity
        writePosition = capacity
    }

    /**
     * Marks all capacity writable except start and end gaps reserved before.
     */
    fun resetForWrite() {
        readPosition = startGap
        writePosition = startGap
    }

    /**
     * Marks up to [limit] bytes of the buffer available for write and no bytes for read.
     * It does respect [startGap] already reserved.
     */
    fun resetForWrite(limit: Int) {
        readPosition = startGap
        writePosition = startGap
        this.limit = limit
    }

    /**
     * Forget start/end gap reservations.
     */
    fun resetGaps() {
        startGap = 0
        limit = capacity
    }

    /**
     * Create a new [Buffer] instance pointing to the same memory and having the same positions.
     */
    fun duplicate(): Buffer = Buffer(memory).also { copy ->
        copy.limit = limit
        copy.startGap = startGap
        copy.readPosition = readPosition
        copy.writePosition = writePosition
    }

    /**
     * Peek the next unsigned byte or return `-1` if no more bytes available for reading.
     */
    fun tryPeek(): Int {
        val readPosition = readPosition
        if (readPosition == writePosition) return -1
        this.readPosition = readPosition + 1
        return memory[readPosition].toInt() and 0xff
    }

    companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [IoBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        const val ReservedSize: Int = 8

        /**
         * The empty buffer singleton: it has zero capacity for read and write.
         */
        val Empty: Buffer = Buffer(Memory.Empty)
    }
}

/**
 * @return `true` if there are available bytes to be read
 */
inline fun Buffer.canRead() = writePosition > readPosition

/**
 * @return `true` if there is free room to for write
 */
inline fun Buffer.canWrite() = limit > writePosition

internal fun discardFailed(count: Int, readRemaining: Int): Nothing {
    throw EOFException("Unable to discard $count bytes: only $readRemaining available for reading")
}

internal fun commitWrittenFailed(count: Int, writeRemaining: Int): Nothing {
    throw EOFException("Unable to discard $count bytes: only $writeRemaining available for writing")
}

internal fun rewindFailed(count: Int, rewindRemaining: Int): Nothing {
    throw EOFException("Unable to rewind $count bytes: only $rewindRemaining could be rewinded")
}

internal fun Buffer.startGapReservationFailedDueToLimit(startGap: Int): Nothing {
    if (startGap > capacity) {
        throw IllegalArgumentException("Start gap $startGap is bigger than the capacity $capacity")
    }

    throw IllegalStateException(
        "Unable to reserve $startGap start gap: there are already ${capacity - limit} bytes reserved in the end"
    )
}

internal fun Buffer.startGapReservationFailed(startGap: Int): Nothing {
    throw IllegalStateException(
        "Unable to reserve $startGap start gap: " +
            "there are already $readRemaining content bytes starting at offset $readPosition"
    )
}

internal fun Buffer.endGapReservationFailedDueToCapacity(endGap: Int) {
    throw IllegalArgumentException("End gap $endGap is too big: capacity is $capacity")
}


internal fun Buffer.endGapReservationFailedDueToStartGap(endGap: Int) {
    throw IllegalArgumentException(
        "End gap $endGap is too big: there are already $startGap bytes reserved in the beginning"
    )
}

internal fun Buffer.endGapReservationFailedDueToContent(endGap: Int) {
    throw IllegalArgumentException(
        "Unable to reserve end gap $endGap:" +
            " there are already $readRemaining content bytes at offset $readPosition"
    )
}
