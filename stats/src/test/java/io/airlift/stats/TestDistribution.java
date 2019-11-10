package io.airlift.stats;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDistribution
{
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
