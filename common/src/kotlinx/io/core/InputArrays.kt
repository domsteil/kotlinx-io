package kotlinx.io.core

import kotlinx.io.core.internal.*
import kotlinx.io.errors.*

fun Input.peekTo(buffer: Buffer): Int {
    TODO_ERROR()
}

fun Input.peekTo(min: Int, buffer: Buffer): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readFully(dst: IoBuffer, length: Int = dst.writeRemaining) {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ByteArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ShortArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: IntArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: LongArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: FloatArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: DoubleArray, offset: Int = 0, length: Int = dst.size - offset): Int {
    TODO_ERROR()
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Input.readAvailable(dst: IoBuffer, length: Int): Int {
    TODO_ERROR()
}

//@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Input.readAvailable(dst: ChunkBuffer, length: Int = dst.writeRemaining): Int {
    TODO_ERROR()
}
