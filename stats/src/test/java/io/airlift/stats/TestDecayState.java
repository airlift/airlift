/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.airlift.stats.DecayConfig.RESCALE_THRESHOLD_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class TestDecayState
{
    private static final double ONE_MINUTE_ALPHA = 1.0 / 60;
    private static final double TOLERANCE = 1e-9;

    private static DecayState decaying(TestingTicker ticker)
    {
        return DecayConfig.of(ONE_MINUTE_ALPHA, ticker).newState();
    }

    @Test
    public void testWeightDecaysOverTime()
    {
        TestingTicker ticker = new TestingTicker();
        DecayState decay = decaying(ticker); // landmark == 0

        assertThat(decay.currentWeight()).isCloseTo(1.0, within(TOLERANCE));

        ticker.increment(60, TimeUnit.SECONDS);
        assertThat(decay.currentWeight()).isCloseTo(Math.E, within(TOLERANCE));

        ticker.increment(60, TimeUnit.SECONDS);
        assertThat(decay.currentWeight()).isCloseTo(Math.E * Math.E, within(TOLERANCE));
    }

    @Test
    public void testWeightAt()
    {
        DecayState decay = decaying(new TestingTicker()); // landmark == 0

        // first call at age 0 must compute 1.0 rather than return the uninitialized cache
        assertThat(decay.weightAt(0)).isEqualTo(1.0);
        assertThat(decay.weightAt(60)).isCloseTo(Math.E, within(TOLERANCE));
    }

    @Test
    public void testNeedsRescale()
    {
        DecayState decay = decaying(new TestingTicker()); // landmark == 0

        assertThat(decay.needsRescale(RESCALE_THRESHOLD_SECONDS - 1)).isFalse();
        assertThat(decay.needsRescale(RESCALE_THRESHOLD_SECONDS)).isTrue();
    }

    @Test
    public void testRescaleToReturnsFactorAndMovesLandmark()
    {
        DecayState decay = decaying(new TestingTicker()); // landmark == 0

        double factor = decay.rescaleTo(60);
        assertThat(factor).isCloseTo(Math.E, within(TOLERANCE));
        assertThat(decay.getLandmarkInSeconds()).isEqualTo(60);

        // weights are now relative to the new landmark
        assertThat(decay.weightAt(60)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(decay.weightAt(120)).isCloseTo(Math.E, within(TOLERANCE));
    }

    @Test
    public void testWeightFromLandmark()
    {
        DecayState decay = decaying(new TestingTicker());
        decay.rescaleTo(100); // landmark == 100

        assertThat(decay.weightFromLandmark(40)).isCloseTo(Math.E, within(TOLERANCE)); // exp(60 / 60)
        assertThat(decay.weightFromLandmark(100)).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    public void testSetLandmark()
    {
        DecayState decay = decaying(new TestingTicker());
        decay.setLandmarkInSeconds(30);

        assertThat(decay.getLandmarkInSeconds()).isEqualTo(30);
        assertThat(decay.weightAt(30)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(decay.weightAt(90)).isCloseTo(Math.E, within(TOLERANCE));
    }

    @Test
    public void testCacheRecomputesAfterLandmarkChange()
    {
        DecayState decay = decaying(new TestingTicker());

        // prime the cache at age 60
        assertThat(decay.weightAt(60)).isCloseTo(Math.E, within(TOLERANCE));

        // moving the landmark changes the age for the same "now", so the cached value must not be reused
        decay.setLandmarkInSeconds(30);
        assertThat(decay.weightAt(60)).isCloseTo(Math.exp(30.0 / 60), within(TOLERANCE));
    }

    @Test
    public void testCacheHitAcrossLandmarkChangeIsCorrect()
    {
        DecayState decay = decaying(new TestingTicker());

        // prime the cache at age 60 (now 60, landmark 0)
        assertThat(decay.weightAt(60)).isCloseTo(Math.E, within(TOLERANCE));

        // a different (now, landmark) pair with the same age must still yield the same weight, since
        // the weight depends only on the age
        decay.setLandmarkInSeconds(50);
        assertThat(decay.weightAt(110)).isCloseTo(Math.E, within(TOLERANCE));
    }

    @Test
    public void testCopyPreservesLandmark()
    {
        DecayState decay = decaying(new TestingTicker());
        decay.rescaleTo(50); // landmark == 50

        DecayState copy = decay.copy();
        assertThat(copy.getAlpha()).isEqualTo(decay.getAlpha());
        assertThat(copy.getLandmarkInSeconds()).isEqualTo(50);

        // mutating the copy does not affect the original
        copy.rescaleTo(80);
        assertThat(decay.getLandmarkInSeconds()).isEqualTo(50);
    }
}
