package io.airlift.stats;

import io.airlift.units.Duration;
import org.testng.annotations.Test;

import static io.airlift.stats.JmxGcMonitor.FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.testng.Assert.assertEquals;

public class TestJmxGcMonitor
{
    @Test
    public void test()
            throws Exception
    {
        JmxGcMonitor gcMonitor = new JmxGcMonitor();
        assertEquals(gcMonitor.getMajorGcCount(), 0);
        assertEquals(gcMonitor.getMajorGcTime(), new Duration(0, NANOSECONDS));
        try {
            gcMonitor.start();
            assertGreaterThanOrEqual(gcMonitor.getMajorGcCount(), (long) 0);
            assertGreaterThanOrEqual(gcMonitor.getMajorGcTime(), new Duration(0, NANOSECONDS));
        }
        finally {
            gcMonitor.stop();
        }
    }

    @Test
    public void testWarnTrigger()
    {
        // Don't warn when max heap is not specified
        assertEquals(JmxGcMonitor.percentOfMaxHeapReclaimed(-1, 100, 100), 100.0);
        // Don't warn when totalBeforeGcMemory / maxHeapMemoryUsage < FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN
        assertEquals(JmxGcMonitor.percentOfMaxHeapReclaimed(100, FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN * 100 - 1, 300), 100.0);
        // Return fraction of heap used when totalBeforeGcMemory / maxHeapMemoryUsage >= FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN
        assertNotEquals(JmxGcMonitor.percentOfMaxHeapReclaimed(100, FRACTION_OF_MAX_HEAP_TO_TRIGGER_WARN * 100, 300), 100.0);
    }
}
