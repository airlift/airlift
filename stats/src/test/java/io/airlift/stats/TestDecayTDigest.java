package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.airlift.stats.DecayTDigest.RESCALE_THRESHOLD_SECONDS;
import static io.airlift.stats.DecayTDigest.ZERO_WEIGHT_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;

public class TestDecayTDigest
{
    @Test
    public void testRescaleMinMax()
    {
        TestingTicker ticker = new TestingTicker();
        DecayTDigest digest = new DecayTDigest(100, 0.001, ticker);

        digest.add(5);
        digest.add(1);

        assertThat(digest.getMin()).isEqualTo(1.0);
        assertThat(digest.getMax()).isEqualTo(5.0);

        ticker.increment(51, TimeUnit.SECONDS);

        assertThat(digest.getMin()).isEqualTo(1.0);
        assertThat(digest.getMax()).isEqualTo(5.0);
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

        assertThat(digest.getCount()).isEqualTo(0.0);
    }

    @Test
    public void testMergeReconcilesLandmarks()
    {
        double decayFactor = 0.1;
        long deltaSeconds = 10; // below RESCALE_THRESHOLD_SECONDS, so no rescale is triggered on its own

        // A value added "now" contributes a decayed count of 1; a value added deltaSeconds earlier
        // contributes exp(-decayFactor * deltaSeconds). The merged count must equal their sum
        // regardless of which operand holds the newer landmark.
        double expectedCount = Math.exp(-decayFactor * deltaSeconds) + 1;

        // older landmark merges in a newer one
        {
            TestingTicker ticker = new TestingTicker();
            DecayTDigest older = new DecayTDigest(100, decayFactor, ticker);
            older.add(10);

            ticker.increment(deltaSeconds, TimeUnit.SECONDS);
            DecayTDigest newer = new DecayTDigest(100, decayFactor, ticker);
            newer.add(20);

            older.merge(newer);

            assertThat(older.getCount()).isCloseTo(expectedCount, Offset.offset(1e-6));
            assertThat(older.getMin()).isEqualTo(10.0);
            assertThat(older.getMax()).isEqualTo(20.0);
        }

        // newer landmark merges in an older one
        {
            TestingTicker ticker = new TestingTicker();
            DecayTDigest older = new DecayTDigest(100, decayFactor, ticker);
            older.add(20);

            ticker.increment(deltaSeconds, TimeUnit.SECONDS);
            DecayTDigest newer = new DecayTDigest(100, decayFactor, ticker);
            newer.add(10);

            newer.merge(older);

            assertThat(newer.getCount()).isCloseTo(expectedCount, Offset.offset(1e-6));
            assertThat(newer.getMin()).isEqualTo(10.0);
            assertThat(newer.getMax()).isEqualTo(20.0);
        }
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
