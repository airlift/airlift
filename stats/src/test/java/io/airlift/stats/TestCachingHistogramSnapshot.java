package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class TestCachingHistogramSnapshot
{
    @Test
    void testSnapshotTimestampsFollowTicker()
    {
        TestingTicker ticker = new TestingTicker();
        ticker.increment(5, SECONDS);
        StripedExponentialHistogram histogram = new StripedExponentialHistogram();
        CachingHistogramSnapshot cache = new CachingHistogramSnapshot(histogram, ticker, SECONDS.toNanos(1));
        histogram.record(10);
        TimedHistogramSnapshot initial = cache.timedSnapshot(false);

        ticker.increment(100, NANOSECONDS);
        assertThat(cache.timedSnapshot(false)).isSameAs(initial);
        TimedHistogramSnapshot forced = cache.timedSnapshot(true);
        assertThat(forced.startEpochNanos()).isEqualTo(initial.startEpochNanos());
        assertThat(forced.epochNanos()).isEqualTo(initial.epochNanos() + 100);

        ticker.increment(1, SECONDS);
        histogram.record(20);
        TimedHistogramSnapshot uncached = cache.uncachedSnapshot();
        assertThat(uncached.startEpochNanos()).isEqualTo(initial.startEpochNanos());
        assertThat(uncached.epochNanos()).isEqualTo(forced.epochNanos() + SECONDS.toNanos(1));
        assertThat(uncached.histogram().count()).isEqualTo(2);
        assertThat(uncached.histogram().sum()).isEqualTo(30);
        assertThat(cache.timedSnapshot(false).epochNanos()).isEqualTo(uncached.epochNanos());
    }

    @Test
    void testResetTimestampsFollowTicker()
    {
        TestingTicker ticker = new TestingTicker();
        StripedExponentialHistogram histogram = new StripedExponentialHistogram();
        CachingHistogramSnapshot cache = new CachingHistogramSnapshot(histogram, ticker, 1);
        histogram.record(10);
        TimedHistogramSnapshot initial = cache.timedSnapshot(true);

        cache.reset();
        TimedHistogramSnapshot reset = cache.timedSnapshot(true);
        assertThat(reset.startEpochNanos()).isEqualTo(initial.epochNanos() + 1);
        assertThat(reset.epochNanos()).isEqualTo(reset.startEpochNanos());
        assertThat(reset.histogram().count()).isZero();

        cache.reset();
        TimedHistogramSnapshot nextReset = cache.timedSnapshot(true);
        assertThat(nextReset.startEpochNanos()).isEqualTo(reset.startEpochNanos() + 1);
        assertThat(nextReset.epochNanos()).isEqualTo(nextReset.startEpochNanos());

        ticker.increment(1, SECONDS);
        histogram.record(20);
        cache.reset();
        TimedHistogramSnapshot advancedReset = cache.timedSnapshot(true);
        assertThat(advancedReset.startEpochNanos()).isEqualTo(initial.epochNanos() + SECONDS.toNanos(1));
        assertThat(advancedReset.epochNanos()).isEqualTo(advancedReset.startEpochNanos());
        assertThat(advancedReset.histogram().count()).isZero();
    }

    @Test
    void testResetAndSnapshotShareGeneration()
            throws Exception
    {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                StripedExponentialHistogram histogram = new StripedExponentialHistogram();
                CachingHistogramSnapshot cache = new CachingHistogramSnapshot(histogram, new TestingTicker(), 1);
                histogram.record(10);
                TimedHistogramSnapshot initial = cache.timedSnapshot(true);
                CountDownLatch start = new CountDownLatch(1);
                var reset = executor.submit(() -> {
                    start.await();
                    cache.reset();
                    return null;
                });
                var snapshot = executor.submit(() -> {
                    start.await();
                    return cache.timedSnapshot(true);
                });
                start.countDown();
                TimedHistogramSnapshot result = snapshot.get();
                reset.get();
                if (result.histogram().count() == 1) {
                    assertThat(result.startEpochNanos()).isEqualTo(initial.startEpochNanos());
                }
                else {
                    assertThat(result.histogram().count()).isZero();
                    assertThat(result.startEpochNanos()).isGreaterThan(initial.startEpochNanos());
                }
                assertThat(result.epochNanos()).isGreaterThanOrEqualTo(result.startEpochNanos());
                assertThat(cache.timedSnapshot(true).startEpochNanos()).isGreaterThan(initial.startEpochNanos());
            }
        }
    }
}
