package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static io.airlift.stats.StatsBackend.OPENTELEMETRY;
import static io.airlift.stats.TimeDistribution.MERGE_THRESHOLD_NANOS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Isolated
@Execution(SAME_THREAD)
class TestTimeDistribution
{
    @BeforeEach
    public void setupStatsBackend()
    {
        StatsBackendFactory.resetForTesting();
        StatsBackendFactory.setBackend(AIRLIFT);
    }

    @AfterEach
    public void resetStatsBackend()
    {
        StatsBackendFactory.resetForTesting();
    }

    @Test
    public void testPartialMerging()
    {
        TestingTicker ticker = new TestingTicker();
        TimeDistribution distribution = new TimeDistribution(ticker);
        distribution.add(SECONDS.toNanos(1));
        distribution.add(SECONDS.toNanos(3));

        assertThat(distribution.getCount()).isEqualTo(0);
        assertThat(distribution.getAvg()).isNaN();

        // This will merge on next get or snapshot
        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge

        assertThat(distribution.getCount()).isEqualTo(2);
        assertThat(distribution.getAvg()).isEqualTo(2);

        distribution.add(SECONDS.toNanos(5));
        ticker.increment(MERGE_THRESHOLD_NANOS / 2, TimeUnit.NANOSECONDS); // time not lapsed enough to merge

        assertThat(distribution.getCount()).isEqualTo(2);
        ticker.increment(MERGE_THRESHOLD_NANOS / 2, TimeUnit.NANOSECONDS); // time lapsed enough to merge

        // This will merge partials again
        assertThat(distribution.snapshot().avg()).isEqualTo(3);

        assertThat(distribution.getCount()).isEqualTo(3);
        assertThat(distribution.getAvg()).isEqualTo(3);
    }

    @Test
    public void testOpenTelemetryBackend()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        TimeDistribution distribution = new TimeDistribution(NANOSECONDS);

        distribution.add(1);
        distribution.add(2);
        distribution.add(4);

        assertThat(distribution.exponentialHistogramSnapshot()).isPresent();
        assertThat(distribution.exponentialHistogramSnapshot().orElseThrow().count()).isEqualTo(3);
        assertThat(distribution.getCount()).isEqualTo(3);
        assertThat(distribution.getMin()).isEqualTo(1);
        assertThat(distribution.getMax()).isEqualTo(4);
        assertThat(distribution.getAvg()).isEqualTo(7.0 / 3);
        assertThat(distribution.getP50()).isBetween(1.0, 4.0);
        assertThat(distribution.snapshot().count()).isEqualTo(3);
    }

    @Test
    public void testOpenTelemetryBackendCachesSnapshots()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        TestingTicker ticker = new TestingTicker();
        TimeDistribution distribution = new TimeDistribution(ticker, NANOSECONDS);

        distribution.add(1);
        assertThat(distribution.getCount()).isEqualTo(1);

        distribution.add(2);
        assertThat(distribution.getCount()).isEqualTo(1);

        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS);
        assertThat(distribution.getCount()).isEqualTo(2);

        distribution.reset();
        assertThat(distribution.getCount()).isEqualTo(0);
    }

    @Test
    void testConcurrentAddsWithConcurrentReader()
            throws Exception
    {
        int threads = 8;
        int addsPerThread = 200_000;

        TimeDistribution distribution = new TimeDistribution(NANOSECONDS);

        AtomicBoolean done = new AtomicBoolean();
        CyclicBarrier barrier = new CyclicBarrier(threads + 1);
        List<Thread> writers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            writers.add(Thread.ofPlatform().start(() -> {
                await(barrier);
                for (int j = 0; j < addsPerThread; j++) {
                    distribution.add(3);
                }
            }));
        }
        // merges race with the writers; an add landing mid-merge must not be dropped
        Thread reader = Thread.ofPlatform().start(() -> {
            await(barrier);
            while (!done.get()) {
                distribution.getPercentiles();
            }
        });
        for (Thread writer : writers) {
            writer.join();
        }
        done.set(true);
        reader.join();

        distribution.getPercentiles(); // force a final merge
        assertThat(distribution.getCount()).isEqualTo((double) threads * addsPerThread);
        assertThat(distribution.getAvg()).isEqualTo(3d);
    }

    private static void await(CyclicBarrier barrier)
    {
        try {
            barrier.await();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
