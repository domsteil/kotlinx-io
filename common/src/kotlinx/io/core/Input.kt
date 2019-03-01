package kotlinx.io.core

import kotlinx.io.core.internal.*

/**
 * Usually shouldn't be implemented directly. Inherit [AbstractInput] instead.
 */
expect interface Input : Closeable {
    @Deprecated(
        "Not supported anymore. All operations are big endian by default.",
        level = DeprecationLevel.ERROR
    )
    var byteOrder: ByteOrder

    /**
     * It is `true` when it is known that no more bytes will be available. When it is `false` then this means that
     * it is not known yet or there are available bytes.
     * Please note that `false` value doesn't guarantee that there are available bytes so `readByte()` may fail.
     */
    val endOfInput: Boolean

    /**
     * Read the next upcoming byte
     * @throws EOFException if no more bytes available.
     */
    fun readByte(): Byte

    /*
     * Returns next byte (unsigned) or `-1` if no more bytes available
     */
    fun tryPeek(): Int

    /**
     * Copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
     * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes then
     * it fails with an exception.
     * It is safe to specify `max > destination.writeRemaining` but
     * `min` shouldn't be bigger than the [destination] free space.
     * This function could trigger the underlying source reading that may lead to blocking I/O.
     * It is safe to specify too big [offset] but only if `min = 0`, fails otherwise.
     * This function usually copy more bytes than [min] (unless `max = min`).
     *
     * @param destination to write bytes
     * @param offset to skip input
     * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
     * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
     * @return number of bytes copied to the [destination] possibly `0`
     * @throws Throwable when not enough bytes available to provide
     */
    fun peekTo(destination: Buffer, offset: Int = 0, min: Int = 1, max: Int = Int.MAX_VALUE): Int

    /**
     * Discard at most [n] bytes
     */
    fun discard(n: Long): Long

    /**
     * Close input including the underlying source. All pending bytes will be discarded.
     * It is not recommended to invoke it with read operations in-progress concurrently.
     */
    override fun close()

    /**
     * Copy available bytes to the specified [buffer] but keep them available.
     * The underlying implementation could trigger
     * bytes population from the underlying source and block until any bytes available.
     *
     * Very similar to [readAvailable] but don't discard copied bytes.
     *
     * @return number of bytes were copied
     */
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("DEPRECATION")
    fun peekTo(buffer: IoBuffer): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readShort(): Short

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readInt(): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readLong(): Long

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFloat(): Float

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readDouble(): Double

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: ByteArray, offset: Int, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: ShortArray, offset: Int, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: IntArray, offset: Int, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: LongArray, offset: Int, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: FloatArray, offset: Int, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: DoubleArray, offset: Int, length: Int)

    @Suppress("DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readFully(dst: IoBuffer, length: Int)

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: ShortArray, offset: Int, length: Int): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: IntArray, offset: Int, length: Int): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: LongArray, offset: Int, length: Int): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: FloatArray, offset: Int, length: Int): Int

    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: DoubleArray, offset: Int, length: Int): Int

    @Suppress("DEPRECATION")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun readAvailable(dst: IoBuffer, length: Int): Int
}

/**
 * Discard all remaining bytes.
 * @return number of bytes were discarded
 */
fun Input.discard(): Long {
    return discard(Long.MAX_VALUE)
}

/**
 * Discard exactly [n] bytes or fail if not enough bytes in the input to be discarded.
 */
fun Input.discardExact(n: Long) {
    val discarded = discard(n)
    if (discarded != n) {
        throw IllegalStateException("Only $discarded bytes were discarded of $n requested")
    }
}

/**
 * Discard exactly [n] bytes or fail if not enough bytes in the input to be discarded.
 */
fun Input.discardExact(n: Int) {
    discardExact(n.toLong())
}

/**
 * Invoke [block] function for every chunk until end of input or [block] function return `false`
 * [block] function returns `true` to request more chunks or `false` to stop loop
 *
 * It is not guaranteed that every chunk will have fixed size but it will be never empty.
 * [block] function should never release provided buffer and should not write to it otherwise an undefined behaviour
 * could be observed
 */
inline fun Input.takeWhile(block: (Buffer) -> Boolean) {
    var release = true
    var current = prepareReadFirstHead(1) ?: return

    try {
        do {
            if (!block(current)) {
                break
            }
            release = false
            val next = prepareReadNextHead(current) ?: break
            current = next
            release = true
        } while (true)
    } finally {
        if (release) {
            completeReadHead(current)
        }
    }
}

/**
 * Invoke [block] function for every chunk until end of input or [block] function return zero
 * [block] function returns number of bytes required to read next primitive and shouldn't require too many bytes at once
 * otherwise it could fail with an exception.
 * It is not guaranteed that every chunk will have fixed size but it will be always at least requested bytes length.
 * [block] function should never release provided buffer and should not write to it otherwise an undefined behaviour
 * could be observed
 */
inline fun Input.takeWhileSize(initialSize: Int = 1, block: (Buffer) -> Int) {
    var release = true
    var current = prepareReadFirstHead(initialSize) ?: return
    var size = initialSize

    try {
        do {
            val before = current.readRemaining
            val after: Int

            if (before >= size) {
                try {
                    size = block(current)
                } finally {
                    after = current.readRemaining
                }
            } else {
                after = before
            }

            release = false

            val next = when {
                after == 0 -> prepareReadNextHead(current)
                after < size || current.endGap < Buffer.ReservedSize -> {
                    completeReadHead(current)
                    prepareReadFirstHead(size)
                }
                else -> current
            }

            if (next == null) {
                release = false
                break
            }

            current = next
            release = true
        } while (size > 0)
    } finally {
        if (release) {
            completeReadHead(current)
        }
    }
}

@ExperimentalIoApi
fun Input.peekCharUtf8(): Char {
    val rc = tryPeek()
    if (rc and 0x80 == 0) return rc.toChar()
    if (rc == -1) throw EOFException("Failed to peek a char: end of input")

    return peekCharUtf8Impl(rc)
}

private fun Input.peekCharUtf8Impl(first: Int): Char {
    var rc = '?'
    var found = false

    takeWhileSize(byteCountUtf8(first)) {
        it.decodeUTF8 { ch ->
            found = true
            rc = ch
            false
        }
    }

    if (!found) {
        throw MalformedUTF8InputException("No UTF-8 character found")
    }

    return rc
}
