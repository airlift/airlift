package io.airlift.stats;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDistribution
{
    @Test
    public void testReset()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(10);
        assertThat(distribution.getCount()).isEqualTo(1d);
        assertThat(distribution.getAvg()).isEqualTo(10d);

        distribution.reset();

        assertThat(distribution.getCount()).isEqualTo(0d);
        assertThat(distribution.getAvg()).isNaN();
    }

    @Test
    public void testConcurrentAdds()
            throws Exception
    {
        int threads = 8;
        int addsPerThread = 10_000;

        // with no decay all values are weighted 1, so count and total must be exact
        Distribution distribution = new Distribution();

        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            workers.add(Thread.ofPlatform().start(() -> {
                try {
                    barrier.await();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
                for (int j = 0; j < addsPerThread; j++) {
                    distribution.add(3);
                }
            }));
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertThat(distribution.getCount()).isEqualTo((double) threads * addsPerThread);
        assertThat(distribution.getTotal()).isEqualTo((double) threads * addsPerThread * 3);
        assertThat(distribution.getAvg()).isEqualTo(3d);
    }

    @Test
    public void testDuplicate()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(100);

        Distribution copy = distribution.duplicate();

        assertThat(copy.getCount()).isEqualTo(distribution.getCount());
        assertThat(copy.getTotal()).isEqualTo(distribution.getTotal());
    }
}
