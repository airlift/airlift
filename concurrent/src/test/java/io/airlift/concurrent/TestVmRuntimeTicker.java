package io.airlift.concurrent;

import com.google.common.base.Ticker;
import io.airlift.testing.TestingTicker;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestVmRuntimeTicker
{
    @Test
    public void testManualAdvance()
            throws Exception
    {
        TestingTicker systemTicker = new TestingTicker();
        VmRuntimeTicker vmRuntimeTicker = new VmRuntimeTicker(systemTicker);

        assertEquals(vmRuntimeTicker.read(), 0);
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);

        vmRuntimeTicker.update(MILLISECONDS.toNanos(1));

        assertEquals(vmRuntimeTicker.read(), MILLISECONDS.toNanos(1));
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);

        vmRuntimeTicker.update(MILLISECONDS.toNanos(1));

        assertEquals(vmRuntimeTicker.read(), MILLISECONDS.toNanos(2));
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);

        vmRuntimeTicker.update(MILLISECONDS.toNanos(500));

        assertEquals(vmRuntimeTicker.read(), MILLISECONDS.toNanos(2));
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 500);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 1.0);

        vmRuntimeTicker.update(MILLISECONDS.toNanos(1));

        assertEquals(vmRuntimeTicker.read(), MILLISECONDS.toNanos(3));
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 500);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 1.0);

        vmRuntimeTicker.update(MILLISECONDS.toNanos(500));

        assertEquals(vmRuntimeTicker.read(), MILLISECONDS.toNanos(3));
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 1000);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 2.0);
    }

    @Test
    public void testBackgroundThreadMonitor()
            throws Exception
    {
        Ticker systemTicker = Ticker.systemTicker();

        VmRuntimeTicker vmRuntimeTicker = new VmRuntimeTicker(systemTicker);
        vmRuntimeTicker.start();

        // Every 10 ms for 5 seconds, verify the VM runtime ticker is within 100 ms of the system ticer
        long systemStartTime = systemTicker.read();
        for (int i = 0; i < 500; i++) {
            Thread.sleep(10);

            // there should be no pauses during this test
            // TODO if these start failing, we'll need to adjust the test
            assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
            assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);

            long systemDuration = systemTicker.read() - systemStartTime;
            long vmRuntime = vmRuntimeTicker.read();

            assertTrue(Math.abs(systemDuration - vmRuntime) < MILLISECONDS.toNanos(100));
        }

        vmRuntimeTicker.stop();
    }

    @Test
    public void testNegativeAdvanceIsIgnored()
            throws Exception
    {
        TestingTicker systemTicker = new TestingTicker();
        VmRuntimeTicker vmRuntimeTicker = new VmRuntimeTicker(systemTicker);

        assertEquals(vmRuntimeTicker.read(), 0);
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);

        vmRuntimeTicker.update(-1000);

        assertEquals(vmRuntimeTicker.read(), 0);
        assertEquals(vmRuntimeTicker.getTotalPauseMillis(), 0);
        assertEquals(vmRuntimeTicker.getPauseTime().getAllTime().getCount(), 0.0);
    }
}
