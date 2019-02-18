package kotlinx.io.core

import kotlinx.io.bits.*
import kotlin.contracts.*

fun Buffer.readShort(): Short = readExact(2, "short integer") { memory, offset ->
    memory.loadShortAt(offset)
}

fun Buffer.readUShort(): UShort = readExact(2, "short unsigned integer") { memory, offset ->
    memory.loadUShortAt(offset)
}

fun Buffer.readInt(): Int = readExact(4, "regular integer") { memory, offset ->
    memory.loadIntAt(offset)
}

fun Buffer.readUInt(): UInt = readExact(4, "regular unsigned integer") { memory, offset ->
    memory.loadUIntAt(offset)
}

fun Buffer.readLong(): Long = readExact(8, "long integer") { memory, offset ->
    memory.loadLongAt(offset)
}

fun Buffer.readULong(): ULong = readExact(8, "long unsigned integer") { memory, offset ->
    memory.loadULongAt(offset)
}

fun Buffer.readFloat(): Float = readExact(4, "floating point number") { memory, offset ->
    memory.loadFloatAt(offset)
}

fun Buffer.readDouble(): Double = readExact(8, "long floating point number") { memory, offset ->
    memory.loadDoubleAt(offset)
}

fun Buffer.writeShort(value: Short): Unit = writeExact(2, "short integer") { memory, offset ->
    memory.storeShortAt(offset, value)
}

fun Buffer.writeUShort(value: UShort): Unit = writeExact(2, "short unsigned integer") { memory, offset ->
    memory.storeUShortAt(offset, value)
}

fun Buffer.writeInt(value: Int): Unit = writeExact(4, "regular integer") { memory, offset ->
    memory.storeIntAt(offset, value)
}

fun Buffer.writeUInt(value: UInt): Unit = writeExact(4, "regular unsigned integer") { memory, offset ->
    memory.storeUIntAt(offset, value)
}

fun Buffer.writeLong(value: Long): Unit = writeExact(8, "long integer") { memory, offset ->
    memory.storeLongAt(offset, value)
}

fun Buffer.writeULong(value: ULong): Unit = writeExact(8, "long unsigned integer") { memory, offset ->
    memory.storeULongAt(offset, value)
}

fun Buffer.writeFloat(value: Float): Unit = writeExact(4, "floating point number") { memory, offset ->
    memory.storeFloatAt(offset, value)
}

fun Buffer.writeDouble(value: Double): Unit = writeExact(8, "long floating point number") { memory, offset ->
    memory.storeDoubleAt(offset, value)
}

@PublishedApi
internal inline fun <R> Buffer.readExact(size: Int, name: String, block: (memory: Memory, offset: Int) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    var value: R
    read { memory, start, endExclusive ->
        kotlinx.io.core.internal.require(endExclusive - start >= size) {
            throw EOFException("Not enough bytes to read a $name of size $size.")
        }
        value = block(memory, start)
        size
    }

    return value
}

@PublishedApi
internal inline fun Buffer.writeExact(size: Int, name: String, block: (memory: Memory, offset: Int) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    write { memory, start, endExclusive ->
        kotlinx.io.core.internal.require(endExclusive - start >= size) {
            throw EOFException("Not enough free space to write a $name of size $size.")
        }
        block(memory, start)
        size
    }
}
