/*
 * Copyright 2010 Proofpoint, Inc.
 *
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.ThreadSafe;
import io.airlift.stats.DecayCounter.DecayCounterSnapshot;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.LongAdder;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static java.util.Objects.requireNonNull;

/**
 * Event statistics.
 * <p>
 * <em>Thread-safety:</em> instances of {@code CounterStat} are thread-safe. However,
 * there is no consistency guarantee when using {@link #reset} or {@link #resetTo} concurrently
 * with {@link #update} or {@link #merge}.
 */
@ThreadSafe
public final class CounterStat
{
    private final LongAdder count = new LongAdder();
    @Nullable
    private final DecayCounter oneMinute;
    @Nullable
    private final DecayCounter fiveMinute;
    @Nullable
    private final DecayCounter fifteenMinute;

    public CounterStat()
    {
        if (StatsBackendFactory.getBackend() == AIRLIFT) {
            oneMinute = new DecayCounter(DecayConfig.oneMinute());
            fiveMinute = new DecayCounter(DecayConfig.fiveMinutes());
            fifteenMinute = new DecayCounter(DecayConfig.fifteenMinutes());
        }
        else {
            oneMinute = null;
            fiveMinute = null;
            fifteenMinute = null;
        }
    }

    public void update(long count)
    {
        if (oneMinute != null && fiveMinute != null && fifteenMinute != null) {
            oneMinute.add(count);
            fiveMinute.add(count);
            fifteenMinute.add(count);
        }
        this.count.add(count);
    }

    public void merge(CounterStat counterStat)
    {
        requireNonNull(counterStat, "counterStat is null");
        if (oneMinute != null && fiveMinute != null && fifteenMinute != null &&
                counterStat.getOneMinute() != null && counterStat.getFiveMinute() != null && counterStat.getFifteenMinute() != null) {
            oneMinute.merge(counterStat.getOneMinute());
            fiveMinute.merge(counterStat.getFiveMinute());
            fifteenMinute.merge(counterStat.getFifteenMinute());
        }
        count.add(counterStat.getTotalCount());
    }

    /**
     * Resets counters.
     * <p>
     * Note: this method is guaranteed to obliterate effect of previous {@link #update} or {@link #merge}
     * invocations. When it invoked concurrently with {@link #update} or {@link #merge}, the result is
     * indeterminate.
     */
    @Managed
    public void reset()
    {
        if (oneMinute != null && fiveMinute != null && fifteenMinute != null) {
            oneMinute.reset();
            fiveMinute.reset();
            fifteenMinute.reset();
        }
        count.reset();
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public void resetTo(CounterStat counterStat)
    {
        requireNonNull(counterStat, "counterStat is null");
        if (oneMinute != null && fiveMinute != null && fifteenMinute != null &&
                counterStat.getOneMinute() != null && counterStat.getFiveMinute() != null && counterStat.getFifteenMinute() != null) {
            oneMinute.resetTo(counterStat.getOneMinute());
            fiveMinute.resetTo(counterStat.getFiveMinute());
            fifteenMinute.resetTo(counterStat.getFifteenMinute());
        }

        synchronized (count) {
            count.reset();
            count.add(counterStat.getTotalCount());
        }
    }

    @Managed
    public long getTotalCount()
    {
        return count.sum();
    }

    @Managed
    @Nested
    @Nullable
    public DecayCounter getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    @Nullable
    public DecayCounter getFiveMinute()
    {
        return fiveMinute;
    }

    @Managed
    @Nested
    @Nullable
    public DecayCounter getFifteenMinute()
    {
        return fifteenMinute;
    }

    public CounterStatSnapshot snapshot()
    {
        return new CounterStatSnapshot(
                getTotalCount(),
                oneMinute == null ? null : oneMinute.snapshot(),
                fiveMinute == null ? null : fiveMinute.snapshot(),
                fifteenMinute == null ? null : fifteenMinute.snapshot());
    }

    public static class CounterStatSnapshot
    {
        private final long totalCount;
        @Nullable
        private final DecayCounterSnapshot oneMinute;
        @Nullable
        private final DecayCounterSnapshot fiveMinute;
        @Nullable
        private final DecayCounterSnapshot fifteenMinute;

        @JsonCreator
        public CounterStatSnapshot(
                @JsonProperty("totalCount") long totalCount,
                @Nullable
                @JsonProperty("oneMinute") DecayCounterSnapshot oneMinute,
                @Nullable
                @JsonProperty("fiveMinute") DecayCounterSnapshot fiveMinute,
                @Nullable
                @JsonProperty("fifteenMinute") DecayCounterSnapshot fifteenMinute)
        {
            this.totalCount = totalCount;
            this.oneMinute = oneMinute;
            this.fiveMinute = fiveMinute;
            this.fifteenMinute = fifteenMinute;
        }

        @JsonProperty
        public long getTotalCount()
        {
            return totalCount;
        }

        @JsonProperty
        @Nullable
        public DecayCounterSnapshot getOneMinute()
        {
            return oneMinute;
        }

        @JsonProperty
        @Nullable
        public DecayCounterSnapshot getFiveMinute()
        {
            return fiveMinute;
        }

        @JsonProperty
        @Nullable
        public DecayCounterSnapshot getFifteenMinute()
        {
            return fifteenMinute;
        }
    }
}
