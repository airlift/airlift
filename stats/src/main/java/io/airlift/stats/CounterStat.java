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
import com.google.common.annotations.Beta;
import io.airlift.stats.DecayCounter.DecayCounterSnapshot;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public class CounterStat
{
    private final AtomicLong count = new AtomicLong(0);
    private final DecayCounter oneMinute = new DecayCounter(ExponentialDecay.oneMinute());
    private final DecayCounter fiveMinute = new DecayCounter(ExponentialDecay.fiveMinutes());
    private final DecayCounter fifteenMinute = new DecayCounter(ExponentialDecay.fifteenMinutes());

    public void update(long count)
    {
        oneMinute.add(count);
        fiveMinute.add(count);
        fifteenMinute.add(count);
        this.count.addAndGet(count);
    }

    public void merge(CounterStat counterStat)
    {
        checkNotNull(counterStat, "counterStat is null");
        oneMinute.merge(counterStat.getOneMinute());
        fiveMinute.merge(counterStat.getFiveMinute());
        fifteenMinute.merge(counterStat.getFifteenMinute());
        count.addAndGet(counterStat.getTotalCount());
    }

    @Managed
    public void reset()
    {
        oneMinute.reset();
        fiveMinute.reset();
        fifteenMinute.reset();
        count.set(0);
    }

    /**
     * This is a hack to work around limitations in Jmxutils.
     */
    @Deprecated
    public void resetTo(CounterStat counterStat)
    {
        oneMinute.resetTo(counterStat.getOneMinute());
        fiveMinute.resetTo(counterStat.getFiveMinute());
        fifteenMinute.resetTo(counterStat.getFifteenMinute());
        count.set(counterStat.getTotalCount());
    }

    @Managed
    public long getTotalCount()
    {
        return count.get();
    }

    @Managed
    @Nested
    public DecayCounter getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    public DecayCounter getFiveMinute()
    {
        return fiveMinute;
    }

    @Managed
    @Nested
    public DecayCounter getFifteenMinute()
    {
        return fifteenMinute;
    }

    public CounterStatSnapshot snapshot()
    {
        return new CounterStatSnapshot(getTotalCount(), getOneMinute().snapshot(), getFiveMinute().snapshot(), getFifteenMinute().snapshot());
    }

    public static class CounterStatSnapshot
    {
        private final long totalCount;
        private final DecayCounterSnapshot oneMinute;
        private final DecayCounterSnapshot fiveMinute;
        private final DecayCounterSnapshot fifteenMinute;

        @JsonCreator
        public CounterStatSnapshot(@JsonProperty("totalCount") long totalCount,
                @JsonProperty("oneMinute") DecayCounterSnapshot oneMinute,
                @JsonProperty("fiveMinute") DecayCounterSnapshot fiveMinute,
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
        public DecayCounterSnapshot getOneMinute()
        {
            return oneMinute;
        }

        @JsonProperty
        public DecayCounterSnapshot getFiveMinute()
        {
            return fiveMinute;
        }

        @JsonProperty
        public DecayCounterSnapshot getFifteenMinute()
        {
            return fifteenMinute;
        }
    }
}
