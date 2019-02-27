package kotlinx.io.core

import kotlinx.io.core.internal.*
import kotlinx.io.errors.*
import org.khronos.webgl.*

fun Input.readFully(dst: Int8Array, offset: Int = 0, length: Int = dst.length - offset) {
    TODO_ERROR()
}

fun Input.readFully(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset) {
    TODO_ERROR()
}

fun Input.readFully(dst: ArrayBufferView, byteOffset: Int = 0, byteLength: Int = dst.byteLength - byteOffset) {
    TODO_ERROR()
}

fun Input.readAvailable(dst: Int8Array, offset: Int = 0, length: Int = dst.length - offset): Int {
    TODO_ERROR()
}

fun Input.readAvailable(dst: ArrayBuffer, offset: Int = 0, length: Int = dst.byteLength - offset): Int {
    TODO_ERROR()
}

fun Input.readAvailable(dst: ArrayBufferView, byteOffset: Int = 0, byteLength: Int = dst.byteLength - byteOffset): Int {
    TODO_ERROR()
}
