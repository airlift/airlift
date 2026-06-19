package io.airlift.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static io.airlift.stats.StatsBackend.OPENTELEMETRY;
import static io.airlift.stats.StatsBackendFactory.STATS_BACKEND_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Isolated
@Execution(SAME_THREAD)
class TestStatsBackendFactory
{
    @BeforeEach
    public void setup()
    {
        System.clearProperty(STATS_BACKEND_PROPERTY);
        StatsBackendFactory.resetForTesting();
    }

    @AfterEach
    public void reset()
    {
        System.clearProperty(STATS_BACKEND_PROPERTY);
        StatsBackendFactory.resetForTesting();
    }

    @Test
    public void testDefaultsToAirlift()
    {
        assertThat(StatsBackendFactory.getBackend()).isEqualTo(AIRLIFT);
    }

    @Test
    public void testReadsSystemProperty()
    {
        System.setProperty(STATS_BACKEND_PROPERTY, "open-telemetry");

        assertThat(StatsBackendFactory.getBackend()).isEqualTo(OPENTELEMETRY);
    }

    @Test
    public void testOpenTelemetryAliases()
    {
        assertThat(StatsBackend.fromPropertyValue("open-telemetry")).isEqualTo(OPENTELEMETRY);
        assertThat(StatsBackend.fromPropertyValue("opentelemetry")).isEqualTo(OPENTELEMETRY);
        assertThat(StatsBackend.fromPropertyValue("otel")).isEqualTo(OPENTELEMETRY);
    }

    @Test
    public void testRejectsUnknownBackend()
    {
        System.setProperty(STATS_BACKEND_PROPERTY, "unknown");

        assertThatThrownBy(StatsBackendFactory::getBackend)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown stats backend: unknown");
    }

    @Test
    public void testCanSetBackendBeforeFirstRead()
    {
        StatsBackendFactory.setBackend(OPENTELEMETRY);

        assertThat(StatsBackendFactory.getBackend()).isEqualTo(OPENTELEMETRY);
    }

    @Test
    public void testCannotSetBackendAfterFirstRead()
    {
        assertThat(StatsBackendFactory.getBackend()).isEqualTo(AIRLIFT);

        assertThatThrownBy(() -> StatsBackendFactory.setBackend(OPENTELEMETRY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stats backend is already initialized");
    }
}
