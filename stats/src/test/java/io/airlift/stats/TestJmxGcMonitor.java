package io.airlift.stats;

import static io.airlift.stats.JmxGcMonitor.FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN;
import static io.airlift.stats.JmxGcMonitor.percentOfMaxHeapReclaimed;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

public class TestJmxGcMonitor {
    @Test
    public void test() throws Exception {
        JmxGcMonitor gcMonitor = new JmxGcMonitor();
        assertThat(gcMonitor.getMajorGcCount()).isEqualTo(0);
        assertThat(gcMonitor.getMajorGcTime()).isEqualTo(new Duration(0, NANOSECONDS));
        try {
            gcMonitor.start();
            assertThat(gcMonitor.getMajorGcCount()).isGreaterThanOrEqualTo(0);
            assertThat(gcMonitor.getMajorGcTime()).isGreaterThanOrEqualTo(new Duration(0, NANOSECONDS));
        } finally {
            gcMonitor.stop();
        }
    }

    @Test
    public void testWarnTrigger() {
        // Don't warn when max heap is not specified
        assertThat(percentOfMaxHeapReclaimed(-1, 100.0, 100.0)).isEqualTo(100.0);
        // Don't warn when totalBeforeGcMemory / maxHeapMemoryUsage < FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN
        assertThat(percentOfMaxHeapReclaimed(100, FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN * 100 - 1, 300))
                .isEqualTo(100.0);
        // Return fraction of heap used when totalBeforeGcMemory / maxHeapMemoryUsage >=
        // FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN
        assertThat(percentOfMaxHeapReclaimed(100, FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN * 100, 300))
                .isLessThan(100.0);
    }
}
