package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.testng.annotations.Test;

import static io.airlift.units.Duration.succinctDuration;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestCpuTimer
{
    @Test
    public void testCpuTimerWithUserTimeEnabled()
    {
        CpuTimer timer = new CpuTimer();
        assertTrue(timer.elapsedTime().hasUser());
        assertTrue(timer.startNewInterval().hasUser());
        assertTrue(timer.elapsedIntervalTime().hasUser());
        assertTrue(timer.elapsedTime().add(new CpuTimer.CpuDuration()).hasUser());
        assertTrue(timer.elapsedTime().subtract(new CpuTimer.CpuDuration()).hasUser());
    }

    @Test
    public void testCpuTimerWithoutUserTimeEnabled()
    {
        CpuTimer timer = new CpuTimer(false);
        assertFalse(timer.elapsedTime().hasUser());
        assertFalse(timer.startNewInterval().hasUser());
        assertFalse(timer.elapsedIntervalTime().hasUser());

        CpuTimer.CpuDuration withUser = new CpuTimer.CpuDuration();
        CpuTimer.CpuDuration withoutUser = timer.elapsedTime();

        assertTrue(withUser.hasUser());
        assertFalse(withoutUser.hasUser());

        assertFalse(withUser.add(withoutUser).hasUser());
        assertFalse(withoutUser.add(withUser).hasUser());

        assertFalse(withUser.subtract(withoutUser).hasUser());
        assertFalse(withoutUser.subtract(withUser).hasUser());
    }

    @Test
    public void testNullTicker()
    {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new CpuTimer(null, true))
                .withMessage("ticker is null");
    }

    @Test
    public void testCustomTicker()
    {
        TestingTicker ticker = new TestingTicker();
        CpuTimer timer = new CpuTimer(ticker, true);
        ticker.increment(1, SECONDS);
        assertEquals(timer.elapsedTime().getWall(), succinctDuration(1, SECONDS));
    }
}
