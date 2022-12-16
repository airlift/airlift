package io.airlift.stats;

import org.testng.annotations.Test;

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
}
