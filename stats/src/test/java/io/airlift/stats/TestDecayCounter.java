package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDecayCounter
{
    @Test
    public void testCountDecays()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        assertThat(Math.abs(counter.getCount() - 1 / Math.E)).isLessThan(1e-9);
    }

    @Test
    public void testAddAfterRescale()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);
        counter.add(2);

        double expected = 2 + 1 / Math.E;
        assertThat(Math.abs(counter.getCount() - expected)).isLessThan(1e-9);
    }

    @Test
    public void testConcurrentAdds()
            throws Exception
    {
        int threads = 8;
        int addsPerThread = 100_000;

        // with no decay the weight is always 1, so the count must be the exact sum of all adds
        DecayCounter counter = new DecayCounter(ExponentialDecay.all());

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
                    counter.add(1);
                }
            }));
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertThat(counter.getCount()).isEqualTo((double) threads * addsPerThread);
    }

    @Test
    public void testDuplicate()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        DecayCounter copy = counter.duplicate();
        assertThat(copy.getCount()).isEqualTo(counter.getCount());
        assertThat(copy.getAlpha()).isEqualTo(counter.getAlpha());
    }
}
