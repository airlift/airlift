package io.airlift.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TestDistribution {
    @Test
    public void testReset() {
        Distribution distribution = new Distribution(0.1);

        distribution.add(10);
        assertThat(distribution.getCount()).isEqualTo(1D);
        assertThat(distribution.getAvg()).isEqualTo(10D);

        distribution.reset();

        assertThat(distribution.getCount()).isEqualTo(0D);
        assertThat(distribution.getAvg()).isNaN();
    }

    @Test
    public void testDuplicate() {
        Distribution distribution = new Distribution(0.1);

        distribution.add(100);

        Distribution copy = distribution.duplicate();

        assertThat(copy.getCount()).isEqualTo(distribution.getCount());
        assertThat(copy.getTotal()).isEqualTo(distribution.getTotal());
    }
}
