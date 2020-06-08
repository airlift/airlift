package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.assertj.core.data.Offset;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static io.airlift.stats.DecayTDigest.RESCALE_THRESHOLD_SECONDS;
import static io.airlift.stats.DecayTDigest.ZERO_WEIGHT_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    public void testDecayBelowThreshold()
    {
        TestingTicker ticker = new TestingTicker();

        double decayFactor = 0.1;
        DecayTDigest digest = new DecayTDigest(100, decayFactor, ticker);

        digest.add(1);

        // incrementing time by this amount should cause the weight of the existing value to become "zero"
        ticker.increment((long) Math.ceil(Math.log(1 / ZERO_WEIGHT_THRESHOLD) / decayFactor), TimeUnit.SECONDS);

        assertEquals(digest.getCount(), 0.0);
    }

    @Test
    public void testDecayBeyondRescaleThreshold()
    {
        TestingTicker ticker = new TestingTicker();

        double decayFactor = 0.1;
        DecayTDigest digest = new DecayTDigest(100, decayFactor, ticker);

        // the weight of this value should decay to 1 after time advances by deltaTime
        digest.add(1, Math.exp(decayFactor * RESCALE_THRESHOLD_SECONDS));

        // advancing the time by this amount will trigger a rescale
        ticker.increment(RESCALE_THRESHOLD_SECONDS, TimeUnit.SECONDS);

        assertThat(digest.getCount())
                .isCloseTo(1.0, Offset.offset(ZERO_WEIGHT_THRESHOLD));

        digest.add(2);

        assertThat(digest.getCount())
                .isCloseTo(2.0, Offset.offset(ZERO_WEIGHT_THRESHOLD));
    }
}
