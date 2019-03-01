package kotlinx.io.core

import kotlinx.io.core.internal.*

/**
 * Creates a temporary packet view of the packet being build without discarding any bytes from the builder.
 * This is similar to `build().copy()` except that the builder keeps already written bytes untouched.
 * A temporary view packet is passed as argument to [block] function and it shouldn't leak outside of this block
 * otherwise an unexpected behaviour may occur.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <R> BytePacketBuilder.preview(block: (tmp: ByteReadPacket) -> R): R {
    val head = head.copyAll()
    val packet = ByteReadPacket(
        head, when {
            head === ChunkBuffer.Empty -> ChunkBuffer.EmptyPool
            else -> _pool
        }
    )

    return try {
        block(packet)
    } finally {
        packet.release()
    }
}
