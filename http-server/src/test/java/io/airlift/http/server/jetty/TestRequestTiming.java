package io.airlift.http.server.jetty;

import io.airlift.http.server.DoubleSummaryStats;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.DoubleSummaryStatistics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestRequestTiming
{
    @Test
    public void testCorrectTimings()
    {
        create(1, 2, 3, 4, 5);
        create(1, 1, 1, 1, 1);
    }

    @Test
    public void testIncorrectTimings()
    {
        assertThatThrownBy(() -> create(2, 1, 2, 2, 2))
                .hasMessageContaining("Expected time from dispatch to handling to increase but got: 2.00ns to 1.00ns");

        assertThatThrownBy(() -> create(2, 2, 1, 2, 2))
                .hasMessageContaining("Expected time from handling to first byte to increase but got: 2.00ns to 1.00ns");

        assertThatThrownBy(() -> create(2, 2, 2, 1, 2))
                .hasMessageContaining("Expected time from first byte to last byte to increase but got: 2.00ns to 1.00ns");

        assertThatThrownBy(() -> create(2, 2, 2, 2, 1))
                .hasMessageContaining("Expected time from dispatch to completion to increase but got: 2.00ns to 1.00ns");
    }

    private static RequestTiming create(
            long timeToDispatch,
            long timeToHandle,
            long timeToFirstByte,
            long timeToLastByte,
            long timeToCompletion)
    {
        return new RequestTiming(
                Instant.now(),
                Duration.succinctNanos(timeToDispatch),
                Duration.succinctNanos(timeToHandle),
                Duration.succinctNanos(timeToFirstByte),
                Duration.succinctNanos(timeToLastByte),
                Duration.succinctNanos(timeToCompletion),
                new DoubleSummaryStats(new DoubleSummaryStatistics()));
    }
}
