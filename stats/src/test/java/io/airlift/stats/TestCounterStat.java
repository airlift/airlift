package io.airlift.stats;

import io.airlift.stats.CounterStat.CounterStatSnapshot;
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
class TestCounterStat
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
    public void testAirliftBackendCreatesDecayCounters()
    {
        CounterStat counter = new CounterStat();
        counter.update(3);

        CounterStatSnapshot snapshot = counter.snapshot();

        assertThat(counter.getTotalCount()).isEqualTo(3);
        assertThat(counter.getOneMinute().getCount()).isEqualTo(3);
        assertThat(counter.getFiveMinute().getCount()).isEqualTo(3);
        assertThat(counter.getFifteenMinute().getCount()).isEqualTo(3);
        assertThat(snapshot.getTotalCount()).isEqualTo(3);
        assertThat(snapshot.getOneMinute()).isNotNull();
        assertThat(snapshot.getFiveMinute()).isNotNull();
        assertThat(snapshot.getFifteenMinute()).isNotNull();
    }

    @Test
    public void testOpenTelemetryBackendCreatesOnlyTotal()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        CounterStat counter = new CounterStat();
        counter.update(3);

        CounterStatSnapshot snapshot = counter.snapshot();

        assertThat(counter.getTotalCount()).isEqualTo(3);
        assertThat(counter.getOneMinute()).isNull();
        assertThat(counter.getFiveMinute()).isNull();
        assertThat(counter.getFifteenMinute()).isNull();
        assertThat(snapshot.getTotalCount()).isEqualTo(3);
        assertThat(snapshot.getOneMinute()).isNull();
        assertThat(snapshot.getFiveMinute()).isNull();
        assertThat(snapshot.getFifteenMinute()).isNull();
    }

    @Test
    public void testOpenTelemetryReset()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        CounterStat counter = new CounterStat();
        counter.update(3);

        counter.reset();

        assertThat(counter.getTotalCount()).isEqualTo(0);
        assertThat(counter.getOneMinute()).isNull();
        assertThat(counter.getFiveMinute()).isNull();
        assertThat(counter.getFifteenMinute()).isNull();
    }

    @Test
    public void testOpenTelemetryMerge()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        CounterStat first = new CounterStat();
        CounterStat second = new CounterStat();
        first.update(3);
        second.update(5);

        first.merge(second);

        assertThat(first.getTotalCount()).isEqualTo(8);
        assertThat(first.getOneMinute()).isNull();
        assertThat(first.getFiveMinute()).isNull();
        assertThat(first.getFifteenMinute()).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testOpenTelemetryResetTo()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);
        CounterStat first = new CounterStat();
        CounterStat second = new CounterStat();
        first.update(3);
        second.update(5);

        first.resetTo(second);

        assertThat(first.getTotalCount()).isEqualTo(5);
        assertThat(first.getOneMinute()).isNull();
        assertThat(first.getFiveMinute()).isNull();
        assertThat(first.getFifteenMinute()).isNull();
    }
}
