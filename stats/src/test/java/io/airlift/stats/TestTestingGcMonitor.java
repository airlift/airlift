
package io.airlift.stats;

import io.airlift.units.Duration;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class TestTestingGcMonitor
{
    @Test
    public void test()
            throws Exception
    {
        TestingGcMonitor gcMonitor = new TestingGcMonitor();

        assertEquals(gcMonitor.getMajorGcCount(), 0);
        assertEquals(gcMonitor.getMajorGcTime(), new Duration(0, SECONDS));

        gcMonitor.recordMajorGc(new Duration(3, SECONDS));

        assertEquals(gcMonitor.getMajorGcCount(), 1);
        assertEquals(gcMonitor.getMajorGcTime(), new Duration(3, SECONDS));

        gcMonitor.recordMajorGc(new Duration(7, SECONDS));

        assertEquals(gcMonitor.getMajorGcCount(), 2);
        assertEquals(gcMonitor.getMajorGcTime(), new Duration(10, SECONDS));
    }
}
