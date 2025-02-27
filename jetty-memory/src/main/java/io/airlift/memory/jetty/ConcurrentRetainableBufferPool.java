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
import org.eclipse.jetty.util.BufferUtil;

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
    private static final long DEFAULT_MAX_MEMORY = DataSize.of(16, MEGABYTE).toBytes();

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
            heapBuckets[bucketId] = new ArenaBucket(false, bucketId);
            offHeapBuckets[bucketId] = new ArenaBucket(true, bucketId);
        }
    }

    @Override
    public RetainableByteBuffer.Mutable acquire(int size, boolean offHeap)
    {
        int bucketId = floorMod(currentThread().threadId(), numBuckets);
        if (offHeap) {
            return offHeapBuckets[bucketId].alloc(size);
        }
        else {
            return heapBuckets[bucketId].alloc(size);
        }
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

    long getOffHeapMemory()
    {
        return getMemory(true);
    }

    long getHeapMemory()
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
        private final int bucketId;
        private final boolean offHeap;
        private final List<FixedSizeBufferPool> pools;

        ArenaBucket(boolean offHeap, int bucketId)
        {
            this.bucketId = bucketId;
            this.offHeap = offHeap;
            this.pools = new ArrayList<>();
        }

        synchronized RetainableByteBuffer.Mutable alloc(int size)
        {
            int poolSizeShift = getPoolSizeShift(size);
            if (poolSizeShift >= pools.size()) {
                addNewPools(poolSizeShift);
            }
            return pools.get(poolSizeShift).allocate();
        }

        private int getPoolSizeShift(int size)
        {
            return max(MIN_POOL_SIZE_POWER, Integer.SIZE - numberOfLeadingZeros(size - 1)) - MIN_POOL_SIZE_POWER;
        }

        private void addNewPools(int poolSizeShift)
        {
            int newPoolSizeShift;
            for (newPoolSizeShift = pools.size(); newPoolSizeShift <= poolSizeShift; newPoolSizeShift++) {
                pools.add(new FixedSizeBufferPool(poolSizeShiftToSize[newPoolSizeShift], offHeap));
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
            for (FixedSizeBufferPool pool : pools) {
                pool.evict();
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
        private int allocatedBuffers;

        FixedSizeBufferPool(int bufferSize, boolean offHeap)
        {
            this.buffers = new ArrayList<>();
            this.bufferSize = bufferSize;
            this.offHeap = offHeap;
        }

        synchronized RetainableByteBuffer.Mutable allocate()
        {
            // For safety, we acquire buffers from the list head, but return them to the tail.
            MemorySegment buffer = buffers.isEmpty() ? allocateNewDirectBuffer() : buffers.removeFirst();
            allocatedBuffers++;
            return RetainableByteBuffer.wrap(toByteBuffer(buffer), () -> free(buffer));
        }

        synchronized void free(MemorySegment buffer)
        {
            if (allocatedBuffers == 0) {
                throw new RuntimeException("Pool has already freed all allocated segments");
            }
            allocatedBuffers--;
            buffers.add(buffer);
            checkMaxMemory(offHeap);
        }

        synchronized void evict()
        {
            buffers.clear();
        }

        private MemorySegment allocateNewDirectBuffer()
        {
            return offHeap ? Arena.ofAuto().allocate(bufferSize, Integer.BYTES) : MemorySegment.ofArray(new byte[bufferSize]);
        }

        private ByteBuffer toByteBuffer(MemorySegment buffer)
        {
            ByteBuffer byteBuffer = buffer.asByteBuffer();
            BufferUtil.reset(byteBuffer);
            return byteBuffer;
        }

        long getMemory()
        {
            return (allocatedBuffers + buffers.size()) * (long) bufferSize;
        }
    }
}
