package io.airlift.stats;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDistribution
{
    @Test
    public void testReset()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(10);
        assertEquals(distribution.getCount(), 1D);
        assertEquals(distribution.getAvg(), 10D);

        distribution.reset();

        assertEquals(distribution.getCount(), 0D);
        assertEquals(distribution.getAvg(), Double.NaN);
    }

    @Test
    public void testDuplicate()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(100);

        Distribution copy = distribution.duplicate();

        assertEquals(copy.getCount(), distribution.getCount());
        assertEquals(copy.getTotal(), distribution.getTotal());
    }
}
