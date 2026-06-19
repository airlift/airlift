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
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import io.airlift.stats.TimeDistribution.TimeDistributionSnapshot;
import io.airlift.units.Duration;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.airlift.stats.StatsBackend.AIRLIFT;
import static java.util.Objects.requireNonNull;

public class TimeStat
{
    @Nullable
    private final TimeDistribution oneMinute;
    @Nullable
    private final TimeDistribution fiveMinutes;
    @Nullable
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
        if (StatsBackendFactory.getBackend() == AIRLIFT) {
            oneMinute = new TimeDistribution(ticker, DecayConfig.oneMinute(), unit);
            fiveMinutes = new TimeDistribution(ticker, DecayConfig.fiveMinutes(), unit);
            fifteenMinutes = new TimeDistribution(ticker, DecayConfig.fifteenMinutes(), unit);
        }
        else {
            oneMinute = null;
            fiveMinutes = null;
            fifteenMinutes = null;
        }
        allTime = new TimeDistribution(ticker, unit);
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
        if (oneMinute != null && fiveMinutes != null && fifteenMinutes != null) {
            oneMinute.add(nanos);
            fiveMinutes.add(nanos);
            fifteenMinutes.add(nanos);
        }
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
    @Nullable
    public TimeDistribution getOneMinute()
    {
        return oneMinute;
    }

    @Managed
    @Nested
    @Nullable
    public TimeDistribution getFiveMinutes()
    {
        return fiveMinutes;
    }

    @Managed
    @Nested
    @Nullable
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
                oneMinute == null ? null : oneMinute.snapshot(),
                fiveMinutes == null ? null : fiveMinutes.snapshot(),
                fifteenMinutes == null ? null : fifteenMinutes.snapshot(),
                getAllTime().snapshot());
    }

    public Optional<ExponentialHistogramSnapshot> exponentialHistogramSnapshot()
    {
        return getAllTime().exponentialHistogramSnapshot();
    }

    @Managed
    public void reset()
    {
        if (oneMinute != null && fiveMinutes != null && fifteenMinutes != null) {
            oneMinute.reset();
            fiveMinutes.reset();
            fifteenMinutes.reset();
        }
        allTime.reset();
    }

    public record TimeDistributionStatSnapshot(
            @Nullable
            TimeDistributionSnapshot oneMinute,
            @Nullable
            TimeDistributionSnapshot fiveMinute,
            @Nullable
            TimeDistributionSnapshot fifteenMinute,
            TimeDistributionSnapshot allTime)
    {
        public TimeDistributionStatSnapshot
        {
            requireNonNull(allTime, "allTime is null");
        }
    }
}
