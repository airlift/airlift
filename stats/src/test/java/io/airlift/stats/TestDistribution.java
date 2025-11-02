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

        assertThat(distribution.snapshot().count()).isEqualTo(1D);
        assertThat(distribution.snapshot().avg()).isEqualTo(10D);

        distribution.reset();
        assertThat(distribution.snapshot().count()).isEqualTo(0D);
        assertThat(distribution.snapshot().avg()).isNaN();
    }
}
