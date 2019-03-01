package kotlinx.io.core

import kotlinx.io.core.internal.*
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

private inline fun <T> T.takeUnless(predicate: (T) -> Boolean): T? {
    return if (!predicate(this)) this else null
}


