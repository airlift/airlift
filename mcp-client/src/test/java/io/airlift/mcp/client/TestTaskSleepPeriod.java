package io.airlift.mcp.client;

import io.airlift.mcp.model.Task;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.client.internal.InternalConnection.taskSleepPeriod;
import static io.airlift.mcp.model.TaskStatus.WORKING;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTaskSleepPeriod
{
    private static final Duration MIN = Duration.ofSeconds(10);
    private static final Duration MAX = Duration.ofMinutes(1);

    @Test
    public void testNoSuggestionUsesMinimum()
    {
        assertThat(taskSleepPeriod(task(OptionalInt.empty()), MIN, MAX)).isEqualTo(MIN);
    }

    @Test
    public void testSuggestionWithinBandIsUsed()
    {
        assertThat(taskSleepPeriod(task(OptionalInt.of(30_000)), MIN, MAX)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    public void testSuggestionBelowBandClampsToMinimum()
    {
        assertThat(taskSleepPeriod(task(OptionalInt.of(1)), MIN, MAX)).isEqualTo(MIN);
    }

    @Test
    public void testSuggestionAboveBandClampsToMaximum()
    {
        // a server asking for a 5 minute interval must not be polled faster than the maximum
        assertThat(taskSleepPeriod(task(OptionalInt.of(300_000)), MIN, MAX)).isEqualTo(MAX);
    }

    @Test
    public void testSuggestionAtBandEdgesIsUsed()
    {
        assertThat(taskSleepPeriod(task(OptionalInt.of((int) MIN.toMillis())), MIN, MAX)).isEqualTo(MIN);
        assertThat(taskSleepPeriod(task(OptionalInt.of((int) MAX.toMillis())), MIN, MAX)).isEqualTo(MAX);
    }

    private static Task task(OptionalInt pollIntervalMs)
    {
        return new Task(
                "task-1",
                WORKING,
                Optional.empty(),
                "2026-08-19T00:00:00Z",
                "2026-08-19T00:00:00Z",
                OptionalInt.empty(),
                pollIntervalMs,
                Optional.empty());
    }
}
