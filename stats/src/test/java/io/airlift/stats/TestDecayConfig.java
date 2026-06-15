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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class TestDecayConfig
{
    private static final double ONE_MINUTE_ALPHA = 1.0 / 60;
    private static final double TOLERANCE = 1e-9;

    @Test
    public void testPresetAlphas()
    {
        assertThat(DecayConfig.oneMinute().alpha()).isEqualTo(1.0 / 60);
        assertThat(DecayConfig.fiveMinutes().alpha()).isEqualTo(1.0 / 300);
        assertThat(DecayConfig.fifteenMinutes().alpha()).isEqualTo(1.0 / 900);
        assertThat(DecayConfig.seconds(10).alpha()).isEqualTo(1.0 / 10);
        assertThat(DecayConfig.of(0.5).alpha()).isEqualTo(0.5);
    }

    @Test
    public void testComputeAlpha()
    {
        // an entry aged 60s should have weight 1/E, which corresponds to the one-minute alpha
        assertThat(DecayConfig.computeAlpha(1 / Math.E, 60)).isCloseTo(ONE_MINUTE_ALPHA, within(TOLERANCE));
    }

    @Test
    public void testComputeAlphaValidation()
    {
        assertThatThrownBy(() -> DecayConfig.computeAlpha(0.5, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetAgeInSeconds must be > 0");
        assertThatThrownBy(() -> DecayConfig.computeAlpha(0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetWeight must be in range (0, 1)");
        assertThatThrownBy(() -> DecayConfig.computeAlpha(1, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetWeight must be in range (0, 1)");
    }

    @Test
    public void testSecondsValidation()
    {
        assertThatThrownBy(() -> DecayConfig.seconds(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seconds must be greater than 1");
        assertThatThrownBy(() -> DecayConfig.seconds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seconds must be greater than 1");
        assertThatThrownBy(() -> DecayConfig.seconds(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seconds must be greater than 1");
    }

    @Test
    public void testAlphaValidation()
    {
        // alpha == 0 (no decay) is not a valid config; the absence of decay is represented by not
        // holding a DecayConfig at all
        assertThatThrownBy(() -> DecayConfig.of(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alpha must be in range (0, 1)");
        assertThatThrownBy(() -> DecayConfig.of(1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alpha must be in range (0, 1)");
        assertThatThrownBy(() -> DecayConfig.of(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alpha must be in range (0, 1)");
    }

    @Test
    public void testNewStateLandmarkStartsAtNow()
    {
        TestingTicker ticker = new TestingTicker();
        ticker.increment(100, TimeUnit.SECONDS);

        DecayConfig config = DecayConfig.of(ONE_MINUTE_ALPHA, ticker);
        DecayState state = config.newState();
        assertThat(state.config()).isSameAs(config);
        assertThat(state.getLandmarkInSeconds()).isEqualTo(100);
        assertThat(state.nowInSeconds()).isEqualTo(100);
    }

    @Test
    public void testNewStateIsIndependentWithFreshLandmark()
    {
        TestingTicker ticker = new TestingTicker();
        DecayConfig config = DecayConfig.of(ONE_MINUTE_ALPHA, ticker);

        DecayState first = config.newState(); // landmark == 0
        ticker.increment(100, TimeUnit.SECONDS);
        DecayState second = config.newState(); // landmark == 100

        assertThat(first.getLandmarkInSeconds()).isEqualTo(0);
        assertThat(second.getLandmarkInSeconds()).isEqualTo(100);

        // the two states are independent: mutating one does not affect the other or the config
        second.rescaleTo(200);
        assertThat(first.getLandmarkInSeconds()).isEqualTo(0);
        assertThat(first.getAlpha()).isEqualTo(config.alpha());
    }

    @Test
    public void testNewStateWithLandmark()
    {
        DecayState state = DecayConfig.of(ONE_MINUTE_ALPHA).newState(42);
        assertThat(state.getAlpha()).isEqualTo(ONE_MINUTE_ALPHA);
        assertThat(state.getLandmarkInSeconds()).isEqualTo(42);
    }
}
