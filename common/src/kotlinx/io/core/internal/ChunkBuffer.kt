package kotlinx.io.core.internal

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import kotlinx.io.bits.Memory
import kotlinx.io.core.Buffer
import kotlinx.io.core.DefaultChunkedBufferPool
import kotlinx.io.pool.ObjectPool

@PublishedApi
internal class ChunkBuffer(memory: Memory, origin: ChunkBuffer?) : Buffer(memory) {
    init {
        require(origin !== this) { "A chunk couldn't be a view of itself." }
    }

    private val nextRef: AtomicRef<ChunkBuffer?> = atomic(null)
    private val refCount = atomic(1)
    var origin: ChunkBuffer? = null
        private set

    val next: ChunkBuffer? get() = nextRef.value
    val referenceCount: Int get() = refCount.value

    fun appendNext(chunk: ChunkBuffer) {
        if (!nextRef.compareAndSet(null, chunk)) {
            throw IllegalStateException("This chunk has already a next chunk.")
        }
    }

    fun cleanNext(): ChunkBuffer? {
        return nextRef.getAndSet(null)
    }

    override fun duplicate(): ChunkBuffer = (origin ?: this).let { newOrigin ->
        newOrigin.acquire()
        ChunkBuffer(memory, newOrigin).also { copy ->
            duplicateTo(copy)
        }
    }

    fun release(pool: ObjectPool<ChunkBuffer>) {
        if (release()) {
            val origin = origin
            if (origin != null) {
                unlink()
                origin.release(pool)
            } else {
                pool.recycle(this)
            }
        }
    }

    internal fun unlink() {
        if (!refCount.compareAndSet(0, -1)) {
            throw IllegalStateException("Unable to unlink: buffer is in use.")
        }

        cleanNext()
        origin = null
    }

    /**
     * Increase ref-count. May fail if already released.
     */
    private fun acquire() {
        refCount.update { old ->
            if (old <= 0) throw IllegalStateException("Unable to acquire chunk: it is already released.")
            old + 1
        }
    }

    /**
     * Invoked by a pool before return the instance to a user.
     */
    internal fun unpark() {
        refCount.update { old ->
            if (old < 0) {
                throw IllegalStateException("This instance is already disposed and couldn't be borrowed.")
            }
            if (old > 0) {
                throw IllegalStateException("This instance is already in use but somehow appeared in the pool.")
            }

            1
        }
    }

    /**
     * Release ref-count.
     * @return `true` if the last usage was released
     */
    private fun release(): Boolean {
        return refCount.updateAndGet { old ->
            if (old <= 0) throw IllegalStateException("Unable to release: it is already released.")
            old - 1
        } == 0
    }

    companion object {
        val Pool: ObjectPool<ChunkBuffer> get() = DefaultChunkedBufferPool
    }
}
