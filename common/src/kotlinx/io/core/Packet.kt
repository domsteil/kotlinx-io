@file:Suppress("RedundantModalityModifier")

package kotlinx.io.core

import kotlinx.io.core.internal.*
import kotlinx.io.errors.*
import kotlinx.io.pool.*

@DangerousInternalIoApi
@Deprecated(
    "Will be removed in the future releases. Use Input or AbstractInput instead.",
    ReplaceWith("AbstractInput", "kotlinx.io.core.AbstractInput")
)
abstract class ByteReadPacketBase(head: ChunkBuffer, remaining: Long, pool: ObjectPool<ChunkBuffer>) :
    AbstractInput(head, remaining, pool)

expect class EOFException(message: String) : IOException

/**
 * For streaming input it should be [Input.endOfInput] instead.
 */
@Deprecated("Use endOfInput property instead", ReplaceWith("endOfInput"))
inline val Input.isEmpty: Boolean
    get() = endOfInput

/**
 * For streaming input there is no reliable way to detect it without triggering bytes population from the underlying
 * source. Consider using [Input.endOfInput] or use [ByteReadPacket] instead.
 */
@Deprecated(
    "This makes no sense for streaming inputs. Some use-cases are covered by endOfInput property",
    ReplaceWith("!endOfInput")
)
val Input.isNotEmpty: Boolean
    get() {
        if (endOfInput) return false
        prepareReadFirstHead(1)?.let { found ->
            completeReadHead(found)
            return true
        }
        return false
    }

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline val ByteReadPacket.isEmpty: Boolean
    get() = endOfInput

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline val ByteReadPacket.isNotEmpty: Boolean
    get() = !endOfInput
