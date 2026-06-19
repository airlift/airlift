package io.airlift.stats;

import io.airlift.stats.DistributionStat.DistributionStatSnapshot;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static io.airlift.stats.StatsBackend.OPENTELEMETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Isolated
@Execution(SAME_THREAD)
class TestDistributionStat
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
    public void testAirliftBackendCreatesAllWindows()
    {
        DistributionStat stat = new DistributionStat();

        stat.add(1);
        stat.add(2, 3);

        assertThat(stat.getOneMinute().getCount()).isEqualTo(4);
        assertThat(stat.getFiveMinutes().getCount()).isEqualTo(4);
        assertThat(stat.getFifteenMinutes().getCount()).isEqualTo(4);
        assertThat(stat.getAllTime().getCount()).isEqualTo(4);
        assertThat(stat.exponentialHistogramSnapshot()).isEmpty();
    }

    @Test
    public void testOpenTelemetryBackendCreatesOnlyAllTime()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        DistributionStat stat = new DistributionStat();

        stat.add(1);
        stat.add(2, 3);

        ExponentialHistogramSnapshot histogramSnapshot = stat.exponentialHistogramSnapshot().orElseThrow();
        DistributionStatSnapshot snapshot = stat.snapshot();

        assertThat(stat.getOneMinute()).isNull();
        assertThat(stat.getFiveMinutes()).isNull();
        assertThat(stat.getFifteenMinutes()).isNull();
        assertThat(stat.getAllTime().getCount()).isEqualTo(4);
        assertThat(snapshot.oneMinute()).isNull();
        assertThat(snapshot.fiveMinute()).isNull();
        assertThat(snapshot.fifteenMinute()).isNull();
        assertThat(snapshot.allTime().count()).isEqualTo(4);
        assertThat(histogramSnapshot.count()).isEqualTo(4);
        assertThat(histogramSnapshot.sum()).isEqualTo(7);
    }
}
