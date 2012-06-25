package io.airlift.stats;

import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

public class TestDecayCounter
{
    @Test
    public void testCountDecays()
    {
        TestingClock clock = new TestingClock();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), clock);
        counter.add(1);
        clock.increment(1, TimeUnit.MINUTES);

        assertTrue(Math.abs(counter.getCount() - 1 / Math.E) < 1e-9);
    }

    @Test
    public void testAddAfterRescale()
    {
        TestingClock clock = new TestingClock();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), clock);
        counter.add(1);
        clock.increment(1, TimeUnit.MINUTES);
        counter.add(2);

        double expected = 2 + 1 / Math.E;
        assertTrue(Math.abs(counter.getCount() - expected) < 1e-9);
    }
}
