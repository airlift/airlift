package io.airlift.stats;

import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import static io.airlift.units.Duration.succinctDuration;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestCpuTimer
{
    @Test
    public void testCpuTimerWithUserTimeEnabled()
    {
        CpuTimer timer = new CpuTimer();
        assertThat(timer.elapsedTime().hasUser()).isTrue();
        assertThat(timer.startNewInterval().hasUser()).isTrue();
        assertThat(timer.elapsedIntervalTime().hasUser()).isTrue();
        assertThat(timer.elapsedTime().add(new CpuTimer.CpuDuration()).hasUser()).isTrue();
        assertThat(timer.elapsedTime().subtract(new CpuTimer.CpuDuration()).hasUser()).isTrue();
    }

    @Test
    public void testCpuTimerWithoutUserTimeEnabled()
    {
        CpuTimer timer = new CpuTimer(false);
        assertThat(timer.elapsedTime().hasUser()).isFalse();
        assertThat(timer.startNewInterval().hasUser()).isFalse();
        assertThat(timer.elapsedIntervalTime().hasUser()).isFalse();

        CpuTimer.CpuDuration withUser = new CpuTimer.CpuDuration();
        CpuTimer.CpuDuration withoutUser = timer.elapsedTime();

        assertThat(withUser.hasUser()).isTrue();
        assertThat(withoutUser.hasUser()).isFalse();

        assertThat(withUser.add(withoutUser).hasUser()).isFalse();
        assertThat(withoutUser.add(withUser).hasUser()).isFalse();

        assertThat(withUser.subtract(withoutUser).hasUser()).isFalse();
        assertThat(withoutUser.subtract(withUser).hasUser()).isFalse();
    }

    @Test
    public void testNullTicker()
    {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new CpuTimer(null, true))
                .withMessage("ticker is null");
    }

    @Test
    public void testCustomTicker()
    {
        TestingTicker ticker = new TestingTicker();
        CpuTimer timer = new CpuTimer(ticker, true);
        ticker.increment(1, SECONDS);
        assertThat(timer.elapsedTime().wall()).isEqualTo(succinctDuration(1, SECONDS));
    }
}
