/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Ticker;
import com.proofpoint.stats.TimeDistribution.TimeDistributionSnapshot;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TimeStat
{
    private final TimeDistribution oneMinute;
    private final TimeDistribution fiveMinutes;
    private final TimeDistribution fifteenMinutes;
    private final TimeDistribution allTime;
    private Ticker ticker;

    public TimeStat()
    {
        this(Ticker.systemTicker());
    }

    public TimeStat(Ticker ticker)
    {
        this.ticker = ticker;
        oneMinute = new TimeDistribution(ExponentialDecay.oneMinute());
        fiveMinutes = new TimeDistribution(ExponentialDecay.fiveMinutes());
        fifteenMinutes = new TimeDistribution(ExponentialDecay.fifteenMinutes());
        allTime = new TimeDistribution();
    }

    public void add(double value, TimeUnit timeUnit)
    {
        add(new Duration(value, timeUnit));
    }

    public void add(Duration duration)
    {
        add((long) duration.toMillis());
    }

    private void add(long value)
    {
        oneMinute.add(value);
        fiveMinutes.add(value);
        fifteenMinutes.add(value);
        allTime.add(value);
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        long start = ticker.read();
        T result = callable.call();
        add(TimeUnit.NANOSECONDS.toMillis(ticker.read() - start));
        return result;
    }

    public BlockTimer time() {
        return new BlockTimer();
    }

    public class BlockTimer implements AutoCloseable
    {
        private final long start = ticker.read();

        @Override
        public void close()
        {
            add(TimeUnit.NANOSECONDS.toMillis(ticker.read() - start));
        }
    }

    @Managed
    @Nested
    public TimeDistribution getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    public TimeDistribution getFiveMinutes()
    {
        return fiveMinutes;
    }

    @Managed
    @Nested
    public TimeDistribution getFifteenMinutes()
    {
        return fifteenMinutes;
    }

    @Managed
    @Nested
    public TimeDistribution getAllTime()
    {
        return allTime;
    }

    public TimeDistributionStatSnapshot snapshot()
    {
        return new TimeDistributionStatSnapshot(
                getOneMinute().snapshot(),
                getFiveMinutes().snapshot(),
                getFifteenMinutes().snapshot(),
                getAllTime().snapshot());
    }

    public static class TimeDistributionStatSnapshot
    {
        private final TimeDistributionSnapshot oneMinute;
        private final TimeDistributionSnapshot fiveMinute;
        private final TimeDistributionSnapshot fifteenMinute;
        private final TimeDistributionSnapshot allTime;

        @JsonCreator
        public TimeDistributionStatSnapshot(
                @JsonProperty("oneMinute") TimeDistributionSnapshot oneMinute,
                @JsonProperty("fiveMinute") TimeDistributionSnapshot fiveMinute,
                @JsonProperty("fifteenMinute") TimeDistributionSnapshot fifteenMinute,
                @JsonProperty("allTime") TimeDistributionSnapshot allTime)
        {
            this.oneMinute = oneMinute;
            this.fiveMinute = fiveMinute;
            this.fifteenMinute = fifteenMinute;
            this.allTime = allTime;
        }

        @JsonProperty
        public TimeDistributionSnapshot getOneMinute()
        {
            return oneMinute;
        }

        @JsonProperty
        public TimeDistributionSnapshot getFiveMinutes()
        {
            return fiveMinute;
        }

        @JsonProperty
        public TimeDistributionSnapshot getFifteenMinutes()
        {
            return fifteenMinute;
        }

        @JsonProperty
        public TimeDistributionSnapshot getAllTime()
        {
            return allTime;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("oneMinute", oneMinute)
                    .add("fiveMinute", fiveMinute)
                    .add("fifteenMinute", fifteenMinute)
                    .add("allTime", allTime)
                    .toString();
        }
    }
}
