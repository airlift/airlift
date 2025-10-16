package io.airlift.stats;

import static org.assertj.core.api.Assertions.assertThat;

import io.airlift.testing.TestingTicker;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class TestDecayCounter {
    @Test
    public void testCountDecays() {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        assertThat(Math.abs(counter.getCount() - 1 / Math.E)).isLessThan(1e-9);
    }

    @Test
    public void testAddAfterRescale() {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);
        counter.add(2);

        double expected = 2 + 1 / Math.E;
        assertThat(Math.abs(counter.getCount() - expected)).isLessThan(1e-9);
    }

    @Test
    public void testDuplicate() {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        DecayCounter copy = counter.duplicate();
        assertThat(copy.getCount()).isEqualTo(counter.getCount());
        assertThat(copy.getAlpha()).isEqualTo(counter.getAlpha());
    }
}
