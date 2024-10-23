package io.airlift.stats;

import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJmxGcMonitor
{
    @Test
    public void test()
            throws Exception
    {
        JmxGcMonitor gcMonitor = new JmxGcMonitor();
        assertThat(gcMonitor.getMajorGcCount()).isEqualTo(0);
        assertThat(gcMonitor.getMajorGcTime()).isEqualTo(new Duration(0, NANOSECONDS));
        try {
            gcMonitor.start();
            assertThat(gcMonitor.getMajorGcCount()).isGreaterThanOrEqualTo(0);
            assertThat(gcMonitor.getMajorGcTime()).isGreaterThanOrEqualTo(new Duration(0, NANOSECONDS));
        }
        finally {
            gcMonitor.stop();
        }
    }
}
