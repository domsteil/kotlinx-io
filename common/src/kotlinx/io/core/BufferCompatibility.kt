@file:Suppress("DeprecatedCallableAddReplaceWith")

package kotlinx.io.core

import kotlinx.io.bits.*


@Deprecated("Use discard with Int parameter. No replacement")
fun Buffer.discard(n: Long): Long = minOf(readRemaining.toLong(), n).toInt().also { discard(it) }.toLong()

/**
 * Copy available bytes to the specified [buffer] but keep them available.
 * If the underlying implementation could trigger
 * bytes population from the underlying source and block until any bytes available
 *
 * Very similar to [readAvailable] but don't discard copied bytes.
 *
 * @return number of bytes were copied
 */
fun Buffer.peekTo(buffer: Buffer): Int {
    val size = minOf(readRemaining, buffer.writeRemaining)
    memory.copyTo(buffer.memory, readPosition, size, buffer.writePosition)
    discard(size)
    buffer.commitWritten(size)
    return size
}

/**
 * Write byte [v] value repeated [n] times.
 */
fun Buffer.fill(n: Long, v: Byte) {
    require(n >= 0)
    require(n <= Int.MAX_VALUE)
    memory.fill(writePosition, n.toInt(), v)
    commitWritten(n.toInt())
}

/**
 * Push back [n] bytes: only possible if there were at least [n] bytes read before this operation.
 */
@Deprecated("Use rewind instead", ReplaceWith("rewind(n)"))
fun Buffer.pushBack(n: Int): Unit = rewind(n)

@Deprecated("Use duplicate instead", ReplaceWith("duplicate()"))
fun Buffer.makeView(): Buffer = duplicate()

@Deprecated("Does nothing.")
fun Buffer.flush() {
}


@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.appendChars(csq: CharArray, start: Int, end: Int): Int = TODO()

@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.appendChars(csq: CharSequence, start: Int, end: Int): Int = TODO()

@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.append(c: Char): Appendable = TODO()

@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.append(csq: CharSequence?): Appendable = TODO()

@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.append(csq: CharSequence?, start: Int, end: Int): Appendable = TODO()

@Deprecated("Not supported anymore", level = DeprecationLevel.ERROR)
fun Buffer.append(csq: CharArray, start: Int, end: Int): Appendable = TODO()

@Deprecated(
    "This is no longer supported. All operations are big endian by default. Use readXXXLittleEndian " +
        "to read primitives in little endian",
    level = DeprecationLevel.ERROR
)
var Buffer.byteOrder: ByteOrder
    get() = ByteOrder.BIG_ENDIAN
    set(newOrder) {
        if (newOrder != ByteOrder.BIG_ENDIAN) throw UnsupportedOperationException("Only BIG_ENDIAN is supported")
    }
