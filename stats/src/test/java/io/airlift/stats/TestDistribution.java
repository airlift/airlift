package io.airlift.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDistribution
{
    @Test
    public void testReset()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(10);
        assertThat(distribution.getCount()).isEqualTo(1d);
        assertThat(distribution.getAvg()).isEqualTo(10d);

        distribution.reset();

        assertThat(distribution.getCount()).isEqualTo(0d);
        assertThat(distribution.getAvg()).isNaN();
    }

    @Test
    public void testDuplicate()
    {
        Distribution distribution = new Distribution(0.1);

        distribution.add(100);

        Distribution copy = distribution.duplicate();

        assertThat(copy.getCount()).isEqualTo(distribution.getCount());
        assertThat(copy.getTotal()).isEqualTo(distribution.getTotal());
    }
}
