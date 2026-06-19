package io.airlift.stats;

import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import static io.airlift.stats.Distribution.MERGE_THRESHOLD_NANOS;
import static io.airlift.stats.StatsBackend.AIRLIFT;
import static io.airlift.stats.StatsBackend.OPENTELEMETRY;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Isolated
@Execution(SAME_THREAD)
public class TestDistribution
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
    public void testDuplicate()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(100);

        Distribution copy = distribution.duplicate();

        assertThat(copy.getCount()).isEqualTo(distribution.getCount());
        assertThat(copy.getTotal()).isEqualTo(distribution.getTotal());
    }

    @Test
    public void testAirliftExponentialHistogramSnapshot()
    {
        Distribution distribution = new Distribution();
        distribution.add(1);

        assertThat(distribution.exponentialHistogramSnapshot()).isEmpty();
    }

    @Test
    public void testOpenTelemetryBackend()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        Distribution distribution = new Distribution();

        distribution.add(1);
        distribution.add(2, 3);

        ExponentialHistogramSnapshot histogramSnapshot = distribution.exponentialHistogramSnapshot().orElseThrow();
        Distribution.DistributionSnapshot snapshot = distribution.snapshot();

        assertThat(distribution.getCount()).isEqualTo(4);
        assertThat(distribution.getTotal()).isEqualTo(7);
        assertThat(distribution.getMin()).isEqualTo(1);
        assertThat(distribution.getMax()).isEqualTo(2);
        assertThat(distribution.getAvg()).isEqualTo(1.75);
        assertThat(distribution.getP50()).isBetween(1.0, 3.0);
        assertThat(distribution.getPercentiles()).hasSize(100);
        assertThat(snapshot.count()).isEqualTo(4);
        assertThat(snapshot.total()).isEqualTo(7);
        assertThat(histogramSnapshot.count()).isEqualTo(4);
        assertThat(histogramSnapshot.sum()).isEqualTo(7);
    }

    @Test
    public void testOpenTelemetryDuplicate()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        Distribution distribution = new Distribution();
        distribution.add(1);
        distribution.add(2, 3);

        Distribution copy = distribution.duplicate();
        distribution.add(10);

        assertThat(copy.getCount()).isEqualTo(4);
        assertThat(copy.getTotal()).isEqualTo(7);
        assertThat(distribution.getCount()).isEqualTo(5);
        assertThat(distribution.getTotal()).isEqualTo(17);
    }

    @Test
    public void testOpenTelemetryBackendCachesSnapshots()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        TestingTicker ticker = new TestingTicker();
        Distribution distribution = new Distribution(ticker);

        distribution.add(1);
        assertThat(distribution.getCount()).isEqualTo(1);

        distribution.add(2);
        assertThat(distribution.getCount()).isEqualTo(1);

        ticker.increment(MERGE_THRESHOLD_NANOS, NANOSECONDS);
        assertThat(distribution.getCount()).isEqualTo(2);

        distribution.reset();
        assertThat(distribution.getCount()).isEqualTo(0);
    }
}
