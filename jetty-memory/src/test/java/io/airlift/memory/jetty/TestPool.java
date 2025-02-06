package io.airlift.memory.jetty;

import io.airlift.units.DataSize;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestPool
{
    private static final int INITIAL_SIZE = 10;
    private static final int SIZE_FACTOR = 10;
    private static final int NUM_SIZES = 6;

    private ConcurrentRetainableBufferPool pool;
    private RetainableByteBuffer[] buffers;

    @BeforeAll
    public void setUp()
    {
        pool = new ConcurrentRetainableBufferPool(0, 0);
        buffers = new RetainableByteBuffer[NUM_SIZES];
    }

    @Test
    public void testBasicAlloc()
    {
        // allocate
        int size = INITIAL_SIZE;
        for (int i = 0; i < buffers.length; i++, size *= SIZE_FACTOR) {
            buffers[i] = pool.acquire(size, i % 2 == 0);
            assertThat(buffers[i].getByteBuffer()).isNotNull();
            assertThat(buffers[i].getByteBuffer().capacity()).isEqualTo(getExpectedSize(size));
        }

        // free
        for (int i = 0; i < buffers.length; i++, size *= 10) {
            buffers[i].release();
            assertThat(buffers[i].getByteBuffer()).isNull();
        }
    }

    @Test
    public void testRetain()
    {
        // allocate retain and release
        int size = INITIAL_SIZE;
        for (int i = 0; i < buffers.length; i++, size *= SIZE_FACTOR) {
            buffers[i] = pool.acquire(size, i % 2 == 0);
            assertThat(buffers[i].getByteBuffer()).isNotNull();
            assertThat(buffers[i].getByteBuffer().capacity()).isEqualTo(getExpectedSize(size));
            assertThat(buffers[i].canRetain()).isTrue();
            buffers[i].retain();
            assertThat(buffers[i].isRetained()).isTrue();
            buffers[i].release();
            assertThat(buffers[i].isRetained()).isFalse();
            assertThat(buffers[i].getByteBuffer()).isNotNull();
        }

        // free
        for (int i = 0; i < buffers.length; i++, size *= 10) {
            buffers[i].release();
            assertThat(buffers[i].getByteBuffer()).isNull();
        }
    }

    @Test
    public void testMaxMemory()
    {
        int bufferIx = 0;

        // allocate a large buffer
        int size = (int) DataSize.of(1, MEGABYTE).toBytes() - 10;
        buffers[bufferIx] = pool.acquire(size, true);
        assertThat(buffers[bufferIx].getByteBuffer()).isNotNull();
        assertThat(buffers[bufferIx].getByteBuffer().capacity()).isEqualTo(getExpectedSize(size));
        bufferIx++;

        // enlarge again
        size *= 4;
        buffers[bufferIx] = pool.acquire(size, true);
        assertThat(buffers[bufferIx].getByteBuffer()).isNotNull();
        assertThat(buffers[bufferIx].getByteBuffer().capacity()).isEqualTo(getExpectedSize(size));
        bufferIx++;

        // free
        while (bufferIx > 0) {
            bufferIx--;
            buffers[bufferIx].release();
            assertThat(buffers[bufferIx].getByteBuffer()).isNull();
        }
    }

    private int getExpectedSize(int size)
    {
        return max(128, 1 << Integer.SIZE - Integer.numberOfLeadingZeros(size - 1));
    }
}
