package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

public class TestDecayTDigest
{
    @Test
    public void testRescaleMinMax()
    {
        TestingTicker ticker = new TestingTicker();
        DecayTDigest digest = new DecayTDigest(100, 0.001, ticker);

        digest.add(5);
        digest.add(1);

        assertEquals(digest.getMin(), 1.0);
        assertEquals(digest.getMax(), 5.0);

        ticker.increment(51, TimeUnit.SECONDS);

        assertEquals(digest.getMin(), 1.0);
        assertEquals(digest.getMax(), 5.0);
    }
}
