package kotlinx.io.core

import kotlinx.io.bits.*

inline fun <R> withBuffer(size: Int, block: Buffer.() -> R): R {
    return with(Buffer(DefaultAllocator.alloc(size)), block)
}

