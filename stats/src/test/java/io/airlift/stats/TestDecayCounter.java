package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDecayCounter
{
    @Test
    public void testCountDecays()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        assertThat(Math.abs(counter.getCount() - 1 / Math.E)).isLessThan(1e-9);
    }

    @Test
    public void testAddAfterRescale()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);
        counter.add(2);

        double expected = 2 + 1 / Math.E;
        assertThat(Math.abs(counter.getCount() - expected)).isLessThan(1e-9);
    }

    @Test
    public void testDuplicate()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        DecayCounter copy = counter.duplicate();
        assertThat(copy.getCount()).isEqualTo(counter.getCount());
        assertThat(copy.getAlpha()).isEqualTo(counter.getAlpha());
    }

    @Test
    public void testAddsVisibleWithoutTimeAdvance()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(1);
        counter.add(2);

        assertThat(counter.getCount()).isEqualTo(3);

        counter.add(4);
        assertThat(counter.getCount()).isEqualTo(7);
    }

    @Test
    public void testDuplicateWithUnfoldedAdds()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(5);

        DecayCounter copy = counter.duplicate();
        assertThat(copy.getCount()).isEqualTo(5);
    }

    @Test
    public void testMergeWithUnfoldedAdds()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        DecayCounter other = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(1);
        other.add(2);

        counter.merge(other);
        assertThat(counter.getCount()).isEqualTo(3);
    }

    @Test
    public void testResetDiscardsUnfoldedAdds()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(DecayConfig.oneMinute(ticker));
        counter.add(42);
        counter.reset();

        assertThat(counter.getCount()).isEqualTo(0);

        counter.add(1);
        assertThat(counter.getCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testResetToResynchronizesPendingSecond()
    {
        TestingTicker sourceTicker = new TestingTicker();
        sourceTicker.increment(10000, TimeUnit.SECONDS);
        DecayCounter source = new DecayCounter(DecayConfig.oneMinute(sourceTicker));
        source.add(100);

        // the target's clock is behind the copied landmark; a stale pendingSecond would
        // make getCount un-decay the copied value by the clock gap
        DecayCounter target = new DecayCounter(DecayConfig.oneMinute(new TestingTicker()));
        target.resetTo(source);

        assertThat(Math.abs(target.getCount() - 100)).isLessThan(1e-9);
    }

    @Test
    public void testNonDecayingCounter()
    {
        DecayCounter counter = new DecayCounter(0.0);
        counter.add(1);
        counter.add(2);

        assertThat(counter.getCount()).isEqualTo(3);

        counter.reset();
        assertThat(counter.getCount()).isEqualTo(0);
    }
}
