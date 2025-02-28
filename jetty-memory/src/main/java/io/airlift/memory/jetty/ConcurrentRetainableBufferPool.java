/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.memory.jetty;

import io.airlift.units.DataSize;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.RetainableByteBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Math.floorMod;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.stream;

public class ConcurrentRetainableBufferPool
        implements ByteBufferPool
{
    private static final long DEFAULT_MAX_MEMORY = DataSize.of(1, MEGABYTE).toBytes();

    // It's not memory efficient to have large pools of very small allocation sizes like 1 or 2 bytes.
    // The minimal buffer size should be large enough to avoid allocations overhead but small enough
    // to avoid wasting bytes if the requested buffers are smaller. We picked minimum allocation size to be 128 bytes.
    private static final int MIN_POOL_SIZE_POWER = 7;
    private static final int[] poolSizeShiftToSize;

    private final ArenaBucket[] heapBuckets;
    private final ArenaBucket[] offHeapBuckets;
    // Aligned with the original Jetty code
    private final AtomicBoolean evictor = new AtomicBoolean(false);
    private final int numBuckets;
    private final int checkMaxMemoryPoint;

    private long maxHeapMemory;
    private long maxOffHeapMemory;
    private int checkCount;

    static {
        poolSizeShiftToSize = new int[Integer.SIZE - MIN_POOL_SIZE_POWER];
        for (int i = 0; i < poolSizeShiftToSize.length; i++) {
            poolSizeShiftToSize[i] = 1 << i + MIN_POOL_SIZE_POWER;
        }
    }

    public ConcurrentRetainableBufferPool(long maxHeapMemory, long maxOffHeapMemory)
    {
        this.numBuckets = Runtime.getRuntime().availableProcessors() * 4;
        // It's not CPU-efficient to check max memory on every free() call.
        // We heuristically take a factor on the number of buckets to decide how many frees we need to observe
        // before we check for max memory. The 100 factor was taken from the original jetty code.
        this.checkMaxMemoryPoint = numBuckets * 100;
        // Both max numbers are adaptive to avoid getting too many evictions.
        // In case max buffer is larger, max memory is updated as well.
        this.maxHeapMemory = maxHeapMemory > 0 ? maxHeapMemory : DEFAULT_MAX_MEMORY;
        this.maxOffHeapMemory = maxOffHeapMemory > 0 ? maxOffHeapMemory : DEFAULT_MAX_MEMORY;

        heapBuckets = new ArenaBucket[numBuckets];
        offHeapBuckets = new ArenaBucket[numBuckets];
        for (int bucketId = 0; bucketId < numBuckets; bucketId++) {
            heapBuckets[bucketId] = new ArenaBucket(this, false, bucketId);
            offHeapBuckets[bucketId] = new ArenaBucket(this, true, bucketId);
        }
    }

    @Override
    public RetainableByteBuffer.Mutable acquire(int size, boolean offHeap)
    {
        int bucketId = floorMod(currentThread().threadId(), numBuckets);
        if (offHeap) {
            return offHeapBuckets[bucketId].alloc(size);
        }
        return heapBuckets[bucketId].alloc(size);
    }

    // Based on the original Jetty code
    private void checkMaxMemory(boolean offHeap)
    {
        long max = offHeap ? maxOffHeapMemory : maxHeapMemory;
        if (max <= 0 || !evictor.compareAndSet(false, true)) {
            return;
        }

        try {
            checkCount++;
            if (checkCount % checkMaxMemoryPoint == 0 && getMemory(offHeap) > max) {
                evict(offHeap);
            }
        }
        finally {
            evictor.set(false);
        }
    }

    private void evict(boolean offHeap)
    {
        if (offHeap) {
            stream(offHeapBuckets).forEach(ArenaBucket::evict);
        }
        else {
            stream(heapBuckets).forEach(ArenaBucket::evict);
        }
    }

    private long getOffHeapMemory()
    {
        return getMemory(true);
    }

    private long getHeapMemory()
    {
        return getMemory(false);
    }

    private long getMemory(boolean offHeap)
    {
        if (offHeap) {
            return stream(offHeapBuckets)
                    .mapToLong(ArenaBucket::getMemory)
                    .sum();
        }
        return stream(heapBuckets)
                .mapToLong(ArenaBucket::getMemory)
                .sum();
    }

    @Override
    public void clear()
    {
        evict(true);
        evict(false);
    }

    @Override
    public String toString()
    {
        return format("%s{onHeap=%d/%d,offHeap=%d/%d}", super.toString(), getHeapMemory(), maxHeapMemory, getOffHeapMemory(), maxOffHeapMemory);
    }

    private class ArenaBucket
    {
        private Arena sharedArena;
        private final Arena autoArena;
        private final int bucketId;
        private final boolean offHeap;
        private final List<FixedSizeBufferPool> pools;
        private final ByteBufferPool parentPool;

        ArenaBucket(ByteBufferPool parentPool, boolean offHeap, int bucketId)
        {
            this.sharedArena = Arena.ofShared();
            this.autoArena = Arena.ofAuto();
            this.bucketId = bucketId;
            this.offHeap = offHeap;
            this.pools = new ArrayList<>();
            this.parentPool = parentPool;
        }

        synchronized RetainableByteBuffer.Mutable alloc(int size)
        {
            int poolSizeShift = getPoolSizeShift(size);
            if (poolSizeShift >= pools.size()) {
                addNewPools(poolSizeShift);
            }
            // We are explicitly avoiding pool of size 32K (shift 8) for shared arenas
            // to circumvent a JDK bug (https://bugs.openjdk.org/browse/JDK-8333849, fixed in JDK 24).
            return pools.get(poolSizeShift).allocate((offHeap && (poolSizeShift == 8)) ? autoArena : sharedArena);
        }

        private int getPoolSizeShift(int size)
        {
            return max(MIN_POOL_SIZE_POWER, Integer.SIZE - numberOfLeadingZeros(size - 1)) - MIN_POOL_SIZE_POWER;
        }

        private void addNewPools(int poolSizeShift)
        {
            int newPoolSizeShift;
            for (newPoolSizeShift = pools.size(); newPoolSizeShift <= poolSizeShift; newPoolSizeShift++) {
                pools.add(new FixedSizeBufferPool(parentPool, poolSizeShiftToSize[newPoolSizeShift], offHeap));
            }
            updateMaxMemoryIfNeeded(poolSizeShiftToSize[newPoolSizeShift] * 16); // heuristically set the maximum to factor of the maximal buffer size
        }

        private void updateMaxMemoryIfNeeded(int newMaxSize)
        {
            if (offHeap) {
                if (newMaxSize > maxOffHeapMemory) {
                    maxOffHeapMemory = newMaxSize;
                }
            }
            else if (newMaxSize > maxHeapMemory) {
                maxHeapMemory = newMaxSize;
            }
        }

        synchronized void evict()
        {
            boolean canClose = offHeap;
            for (FixedSizeBufferPool pool : pools) {
                pool.evict();
                canClose &= pool.getBufferCount() == 0;
            }
            if (canClose) {
                // Free shared arena and create a new one
                sharedArena.close();
                sharedArena = Arena.ofShared();
            }
        }

        synchronized long getMemory()
        {
            return pools.stream()
                    .mapToLong(FixedSizeBufferPool::getMemory)
                    .sum();
        }

        @Override
        public String toString()
        {
            return format("%s{bucketId=%d,offHeap=%b,#pools=%d}", super.toString(), bucketId, offHeap, pools.size());
        }
    }

    // Similar the Jetty's ArrayByteBufferPool.Quadratic
    private class FixedSizeBufferPool
    {
        private final List<MemorySegment> buffers;
        private final int bufferSize;
        private final boolean offHeap;
        private final ByteBufferPool parentPool;
        private int allocatedBuffers;

        FixedSizeBufferPool(ByteBufferPool parentPool, int bufferSize, boolean offHeap)
        {
            this.buffers = new ArrayList<>();
            this.bufferSize = bufferSize;
            this.offHeap = offHeap;
            this.parentPool = parentPool;
        }

        synchronized Buffer allocate(Arena arena)
        {
            // For safety, we acquire buffers from the list head, but return them to the tail.
            MemorySegment buffer = buffers.isEmpty() ? allocateNewBuffer(arena) : buffers.removeFirst();
            allocatedBuffers++;
            return new Buffer(buffer, buffer.asByteBuffer(), this);
        }

        synchronized void free(MemorySegment buffer)
        {
            if (allocatedBuffers == 0) {
                throw new RuntimeException("Pool has already freed all allocated segments");
            }
            allocatedBuffers--;
            buffers.add(buffer);
        }

        synchronized void evict()
        {
            buffers.clear();
        }

        void clear(ByteBuffer byteBuffer)
        {
            byteBuffer.limit(0);
            byteBuffer.position(0);
        }

        private MemorySegment allocateNewBuffer(Arena arena)
        {
            return offHeap ? arena.allocate(bufferSize, Integer.BYTES) : MemorySegment.ofArray(new byte[bufferSize]);
        }

        long getMemory()
        {
            return (allocatedBuffers + buffers.size()) * (long) bufferSize;
        }

        int getBufferCount()
        {
            return allocatedBuffers + buffers.size();
        }

        boolean isOffHeap()
        {
            return offHeap;
        }

        ByteBufferPool getParentPool()
        {
            return parentPool;
        }
    }

    // Similar the Jetty's ArrayByteBufferPool.Quadratic
    private class Buffer
            extends RetainableByteBuffer.Pooled
    {
        private final MemorySegment buffer;
        private final FixedSizeBufferPool pool;
        private ByteBuffer byteBuffer;

        Buffer(MemorySegment buffer, ByteBuffer byteBuffer, FixedSizeBufferPool pool)
        {
            super(pool.getParentPool(), byteBuffer, new ReferenceCounter());
            this.buffer = buffer;
            this.byteBuffer = byteBuffer;
            this.pool = pool;
            clear();
        }

        @Override
        public boolean release()
        {
            if (byteBuffer == null) {
                return true;
            }
            boolean released = super.release();
            if (released) {
                pool.free(buffer);
                byteBuffer = null;
                checkMaxMemory(pool.isOffHeap());
            }
            return released;
        }

        @Override
        public ByteBuffer getByteBuffer()
        {
            if (byteBuffer == null) {
                return null;
            }
            return super.getByteBuffer();
        }

        @Override
        public void clear()
        {
            if (byteBuffer != null) {
                pool.clear(byteBuffer);
            }
        }
    }
}
