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
package io.airlift.stats;

import com.google.common.base.Ticker;
import io.airlift.stats.TimeDistribution.TimeDistributionSnapshot;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class TimeStat
{
    private final TimeDistribution oneMinute;
    private final TimeDistribution fiveMinutes;
    private final TimeDistribution fifteenMinutes;
    private final TimeDistribution allTime;
    private final Ticker ticker;

    public TimeStat()
    {
        this(Ticker.systemTicker(), TimeUnit.SECONDS);
    }

    public TimeStat(Ticker ticker)
    {
        this(ticker, TimeUnit.SECONDS);
    }

    public TimeStat(TimeUnit unit)
    {
        this(Ticker.systemTicker(), unit);
    }

    public TimeStat(Ticker ticker, TimeUnit unit)
    {
        this.ticker = ticker;
        oneMinute = new TimeDistribution(ExponentialDecay.oneMinute(), unit);
        fiveMinutes = new TimeDistribution(ExponentialDecay.fiveMinutes(), unit);
        fifteenMinutes = new TimeDistribution(ExponentialDecay.fifteenMinutes(), unit);
        allTime = new TimeDistribution(unit);
    }

    public void add(double value, TimeUnit timeUnit)
    {
        requireNonNull(timeUnit, "timeUnit is null");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value is not finite: " + value);
        }
        if (value < 0) {
            throw new IllegalArgumentException("value is negative: " + value);
        }
        addNanos((long) Math.floor((value * timeUnit.toNanos(1)) + 0.5d));
    }

    public void add(Duration duration)
    {
        addNanos((long) duration.getValue(TimeUnit.NANOSECONDS));
    }

    public void addNanos(long nanos)
    {
        if (nanos < 0) {
            throw new IllegalArgumentException("value is negative: " + nanos);
        }
        oneMinute.add(nanos);
        fiveMinutes.add(nanos);
        fifteenMinutes.add(nanos);
        allTime.add(nanos);
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        long start = ticker.read();
        try {
            return callable.call();
        }
        finally {
            addNanos(ticker.read() - start);
        }
    }

    public BlockTimer time()
    {
        return new BlockTimer();
    }

    public class BlockTimer
            implements AutoCloseable
    {
        private final long start = ticker.read();

        @Override
        public void close()
        {
            addNanos(ticker.read() - start);
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

    @Managed
    public void reset()
    {
        oneMinute.reset();
        fiveMinutes.reset();
        fifteenMinutes.reset();
        allTime.reset();
    }

    public record TimeDistributionStatSnapshot(
            TimeDistributionSnapshot oneMinute,
            TimeDistributionSnapshot fiveMinute,
            TimeDistributionSnapshot fifteenMinute,
            TimeDistributionSnapshot allTime)
    {
        public TimeDistributionStatSnapshot
        {
            requireNonNull(oneMinute, "oneMinute is null");
            requireNonNull(fiveMinute, "fiveMinute is null");
            requireNonNull(fifteenMinute, "fifteenMinute is null");
            requireNonNull(allTime, "allTime is null");
        }
    }
}
