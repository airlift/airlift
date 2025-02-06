package io.airlift.memory.jetty;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.eclipse.jetty.util.BufferUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ArrayByteBufferPool
        implements ByteBufferPool
{
    private static final int MIN_BUCKET_IX = 7;  // inclusive
    private static final int MAX_BUCKET_IX = 25; // exclusive
    private static final int NUM_BUCKETS = MAX_BUCKET_IX - MIN_BUCKET_IX;
    private static final long DEFAULT_MAX_HEAP_MEMORY = 8 * 1024 * 1024;
    private static final long DEFAULT_MAX_OFF_HEAP_MEMORY = 8 * 1024 * 1024;

    private final ArenaBucket[] offHeapBuckets;
    private final ArenaBucket[] heapBuckets;
    private final long maxHeapMemory;
    private final long maxOffHeapMemory;
    private final AtomicBoolean evictor = new AtomicBoolean(false);

    /**
     * Creates a new ArrayByteBufferPool with the given configuration.
     */
    public ArrayByteBufferPool(int maxBufferSize, long maxHeapMemory, long maxOffHeapMemory)
    {
        if (maxBufferSize > 1 << MAX_BUCKET_IX) {
            throw new RuntimeException("maxBufferSize " + maxBufferSize + " too large");
        }
        if (maxHeapMemory <= 0) {
            maxHeapMemory = DEFAULT_MAX_HEAP_MEMORY;
        }
        if (maxOffHeapMemory <= 0) {
            maxOffHeapMemory = DEFAULT_MAX_OFF_HEAP_MEMORY;
        }
        this.maxHeapMemory = maxHeapMemory;
        this.maxOffHeapMemory = maxOffHeapMemory;

        this.offHeapBuckets = new ArenaBucket[NUM_BUCKETS];
        this.heapBuckets = new ArenaBucket[NUM_BUCKETS];
        for (int i = 0; i < NUM_BUCKETS; i++) {
            int size = 1 << i + MIN_BUCKET_IX;
            offHeapBuckets[i] = new ArenaBucket(size, true);
            heapBuckets[i] = new ArenaBucket(size, false);
        }
    }

    @Override
    public RetainableByteBuffer acquire(int size, boolean isOffHeap)
    {
        ArenaBucket bucket = bucketFor(size, isOffHeap);
        Buffer buffer = bucket.alloc();
        return (RetainableByteBuffer) buffer;
    }

    private ArenaBucket bucketFor(int size, boolean isOffHeap)
    {
        int bucketIx = Math.max(MIN_BUCKET_IX, 32 - Integer.numberOfLeadingZeros(size - 1));
        bucketIx -= MIN_BUCKET_IX;
        if (bucketIx >= NUM_BUCKETS) {
            throw new RuntimeException("buffer too large " + size);
        }
        return isOffHeap ? offHeapBuckets[bucketIx] : heapBuckets[bucketIx];
    }

    private void checkMaxMemory(boolean isOffHeap)
    {
        long max = isOffHeap ? maxOffHeapMemory : maxHeapMemory;
        if (max <= 0 || !evictor.compareAndSet(false, true)) {
            return;
        }

        try {
            long memory = getMemory(isOffHeap);
            long excess = memory - max;
            if (excess > 0) {
                evict(excess, isOffHeap);
            }
        }
        finally {
            evictor.set(false);
        }
    }

    private void evict(long excessMemory, boolean isOffHeap)
    {
        ArenaBucket[] buckets = isOffHeap ? offHeapBuckets : heapBuckets;
        int length = buckets.length;
        for (int bucketIx = buckets.length - 1; bucketIx >= 0; bucketIx--) {
            ArenaBucket bucket = buckets[bucketIx];
            excessMemory -= bucket.evict();
            if (excessMemory <= 0) {
                return;
            }
        }
    }

    public long getDirectByteBufferCount()
    {
        return getByteBufferCount(true);
    }

    public long getHeapByteBufferCount()
    {
        return getByteBufferCount(false);
    }

    private long getByteBufferCount(boolean isOffHeap)
    {
        ArenaBucket[] buckets = isOffHeap ? offHeapBuckets : heapBuckets;
        return Arrays.stream(buckets).mapToLong(bucket -> bucket.getBuffersCount()).sum();
    }

    public long getAvailableDirectByteBufferCount()
    {
        return getAvailableByteBufferCount(true);
    }

    public long getAvailableHeapByteBufferCount()
    {
        return getAvailableByteBufferCount(false);
    }

    private long getAvailableByteBufferCount(boolean isOffHeap)
    {
        ArenaBucket[] buckets = isOffHeap ? offHeapBuckets : heapBuckets;
        return Arrays.stream(buckets).mapToLong(bucket -> bucket.getAvailableBuffersCount()).sum();
    }

    public long getDirectMemory()
    {
        return getMemory(true);
    }

    public long getHeapMemory()
    {
        return getMemory(false);
    }

    private long getMemory(boolean isOffHeap)
    {
        long size = 0;
        for (ArenaBucket bucket : isOffHeap ? offHeapBuckets : heapBuckets) {
            size += (long) bucket.getMemory();
        }
        return size;
    }

    public long getAvailableDirectMemory()
    {
        return getDirectMemory();
    }

    public long getAvailableHeapMemory()
    {
        return getHeapMemory();
    }

    private long getAvailableMemory(boolean isOffHeap)
    {
        long size = 0;
        for (ArenaBucket bucket : isOffHeap ? offHeapBuckets : heapBuckets) {
            size += (long) bucket.getAvailableMemory();
        }
        return size;
    }

    public void clear()
    {
        clearBuckets(offHeapBuckets);
        clearBuckets(heapBuckets);
    }

    private void clearBuckets(ArenaBucket[] buckets)
    {
        for (ArenaBucket bucket : buckets) {
            bucket.evict();
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s{buckets=%d,heap=%d/%d,offheap=%d/%d}",
            super.toString(),
            offHeapBuckets.length,
            getHeapMemory(),
            maxHeapMemory,
            getDirectMemory(),
            maxOffHeapMemory);
    }

    private class ArenaBucket
    {
        private final List<ByteBuffer> bufferList;
        private final int bufferSize;
        private final boolean isOffHeap;
        private int numAllocatedBuffers;

        ArenaBucket(int bufferSize, boolean isOffHeap)
        {
            this.bufferList = new ArrayList<>();
            this.bufferSize = bufferSize;
            this.isOffHeap = isOffHeap;
        }

        synchronized Buffer alloc()
        {
            int numBuffers = bufferList.size();
            ByteBuffer byteBuffer = (numBuffers == 0) ? BufferUtil.allocate(bufferSize, isOffHeap) : bufferList.remove(0);
            byteBuffer.position(0); // this is a requirement to return the byte buffer with these attributes
            byteBuffer.limit(0);    // this is a requirement to return the byte buffer with these attributes
            numAllocatedBuffers++;
            return new Buffer(byteBuffer, this);
        }

        synchronized void free(ByteBuffer byteBuffer)
        {
            if (numAllocatedBuffers == 0) {
                throw new RuntimeException("bucket is already without allocated buffers");
            }
            numAllocatedBuffers--;
            bufferList.add(byteBuffer);
            checkMaxMemory(isOffHeap);
        }

        synchronized long evict()
        {
            long availableMemory = getAvailableMemory();
            bufferList.clear();
            return availableMemory;
        }

        long getMemory()
        {
            return (numAllocatedBuffers + bufferList.size()) * (long) bufferSize;
        }

        long getAvailableMemory()
        {
            return bufferList.size() * (long) bufferSize;
        }

        int getBuffersCount()
        {
            return numAllocatedBuffers + bufferList.size();
        }

        int getAvailableBuffersCount()
        {
            return bufferList.size();
        }
    }

    private class Buffer
            implements RetainableByteBuffer
    {
        private final AtomicInteger refCount;
        private ByteBuffer byteBuffer;
        private ArenaBucket bucket;

        Buffer(ByteBuffer byteBuffer, ArenaBucket bucket)
        {
            this.refCount = new AtomicInteger(1);
            this.byteBuffer = byteBuffer;
            this.bucket = bucket;
        }

        @Override
        public void retain()
        {
            if (byteBuffer == null) {
                throw new IllegalStateException("buffer cannot be retained since already released");
            }
            refCount.getAndUpdate(c -> c + 1);
        }

        @Override
        public boolean release()
        {
            if (byteBuffer == null) {
                return true; // idiom potent
            }
            boolean shouldRelease = refCount.updateAndGet(c -> c - 1) == 0;
            if (shouldRelease) {
                bucket.free(byteBuffer);
                byteBuffer = null; // safety
                bucket = null; // safety
            }
            return shouldRelease;
        }

        @Override
        public boolean canRetain()
        {
            return true;
        }

        @Override
        public boolean isRetained()
        {
            return refCount.get() > 1;
        }

        @Override
        public ByteBuffer getByteBuffer()
        {
            return byteBuffer;
        }
    }
}
