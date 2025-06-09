package io.airlift.memory.jetty;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.RetainableByteBuffer;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.function.IntUnaryOperator;

public class UnsafeArrayByteBufferPool
        extends ArrayByteBufferPool
{
    private static final Unsafe UNSAFE;
    private static final MethodHandle INVOKE_CLEANER;

    static {
        MethodHandle invokeCleaner;
        Unsafe unsafeInstance;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafeInstance = (Unsafe) field.get(null);
            if (unsafeInstance == null) {
                throw new IllegalAccessException("Unsafe access not available");
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType methodType = MethodType.methodType(void.class, ByteBuffer.class);
            // Unsafe.invokeCleaner(ByteBuffer) is a JDK12+ API that is deprecated and to be removed in future JDK versions.
            // With JDK 25 we should be able to use MemorySegments with shared arenas instead.
            invokeCleaner = lookup.findVirtual(unsafeInstance.getClass(), "invokeCleaner", methodType);
        }
        catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException ignored) {
            invokeCleaner = null;
            unsafeInstance = null;
        }
        INVOKE_CLEANER = invokeCleaner;
        UNSAFE = unsafeInstance;
    }

    public static boolean isUnsafeAvailable()
    {
        return INVOKE_CLEANER != null;
    }

    public UnsafeArrayByteBufferPool(int minCapacity, int factor, int maxCapacity, int maxBucketSize, long maxHeapMemory, long maxDirectMemory, IntUnaryOperator bucketIndexFor, IntUnaryOperator bucketCapacity)
    {
        super(minCapacity, factor, maxCapacity, maxBucketSize, maxHeapMemory, maxDirectMemory, bucketIndexFor, bucketCapacity);
    }

    @Override
    public boolean removeAndRelease(RetainableByteBuffer buffer)
    {
        boolean released = super.removeAndRelease(buffer);
        RetainableByteBuffer actual = buffer;
        while (actual instanceof RetainableByteBuffer.Wrapper wrapper) {
            actual = wrapper.getWrapped();
        }

        if (actual.isDirect()) {
            // If the buffer is direct, we can clean it up
            // This is a workaround for the fact that Jetty's ArrayByteBufferPool does not clean up direct buffers
            try {
                INVOKE_CLEANER.invoke(UNSAFE, actual.getByteBuffer());
            }
            catch (Throwable ignored) {
                // Ignore any exceptions from invokeCleaner
            }
        }

        return released;
    }

    public static class Quadratic
            extends UnsafeArrayByteBufferPool
    {
        public Quadratic()
        {
            this(0, -1, Integer.MAX_VALUE);
        }

        public Quadratic(int minCapacity, int maxCapacity, int maxBucketSize)
        {
            this(minCapacity, maxCapacity, maxBucketSize, -1L, -1L);
        }

        public Quadratic(int minCapacity, int maxCapacity, int maxBucketSize, long maxHeapMemory, long maxDirectMemory)
        {
            super(minCapacity,
                    -1,
                    maxCapacity,
                    maxBucketSize,
                    maxHeapMemory,
                    maxDirectMemory,
                    c -> 32 - Integer.numberOfLeadingZeros(c - 1),
                    i -> 1 << i);
        }
    }
}
