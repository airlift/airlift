
package io.airlift.stats;

import io.airlift.units.Duration;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTestingGcMonitor
{
    @Test
    public void test()
            throws Exception
    {
        TestingGcMonitor gcMonitor = new TestingGcMonitor();

        assertThat(gcMonitor.getMajorGcCount()).isEqualTo(0);
        assertThat(gcMonitor.getMajorGcTime()).isEqualTo(new Duration(0, SECONDS));

        gcMonitor.recordMajorGc(new Duration(3, SECONDS));

        assertThat(gcMonitor.getMajorGcCount()).isEqualTo(1);
        assertThat(gcMonitor.getMajorGcTime()).isEqualTo(new Duration(3, SECONDS));

        gcMonitor.recordMajorGc(new Duration(7, SECONDS));

        assertThat(gcMonitor.getMajorGcCount()).isEqualTo(2);
        assertThat(gcMonitor.getMajorGcTime()).isEqualTo(new Duration(10, SECONDS));
    }
}
