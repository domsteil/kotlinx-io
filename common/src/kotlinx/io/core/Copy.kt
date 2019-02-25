package kotlinx.io.core

import kotlinx.io.core.internal.*

/**
 * Copy all bytes to the [output].
 * Depending on actual input and output implementation it could be zero-copy or copy byte per byte.
 * All regular types such as [ByteReadPacket], [BytePacketBuilder], [AbstractInput] and [AbstractOutput]
 * are always optimized so no bytes will be copied.
 */
fun Input.copyTo(output: Output): Long {
    @Suppress("DEPRECATION")
    if (this !is ByteReadPacketBase || output !is BytePacketBuilderBase) {
        // slow-path
        return copyToFallback(output)
    }

    var copied = 0L
    do {
        val head = stealAll()
        if (head == null) {
            if (prepareRead(1) == null) break
            continue
        }

        copied += head.remainingAll()
        output.last(head)
    } while (true)

    return copied
}

private fun Input.copyToFallback(output: Output): Long {
    val buffer = ChunkBuffer.Pool.borrow()
    var copied = 0L

    try {
        do {
            buffer.resetForWrite()
            val rc = readAvailable(buffer)
            if (rc == -1) break
            copied += rc
            output.writeFully(buffer)
        } while (true)

        return copied
    } finally {
        buffer.release(ChunkBuffer.Pool)
    }
}
