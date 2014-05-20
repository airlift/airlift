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

import com.google.common.base.Function;
import com.google.common.base.Ticker;
import com.proofpoint.reporting.Bucketed;
import com.proofpoint.reporting.Reported;
import com.proofpoint.units.Duration;

import javax.annotation.concurrent.GuardedBy;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class SparseTimeStat
    extends Bucketed<SparseTimeStat.Distribution>
{
    private final Ticker ticker;

    public SparseTimeStat()
    {
        this(Ticker.systemTicker());
    }

    public SparseTimeStat(Ticker ticker)
    {
        this.ticker = ticker;
    }

    public void add(long value, TimeUnit timeUnit)
    {
        add(timeUnit.toNanos(value));
    }

    public void add(double value, TimeUnit timeUnit)
    {
        add(new Duration(value, timeUnit).roundTo(NANOSECONDS));
    }

    public void add(Duration duration)
    {
        add(duration.roundTo(NANOSECONDS));
    }

    private void add(final long value)
    {
        applyToCurrentBucket(new Function<Distribution, Void>()
        {
            @Override
            public Void apply(Distribution input)
            {
                synchronized (input) {
                    input.digest.add(value);
                    input.total += value;
                }
                return null;
            }
        });
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        try (BlockTimer ignored = time()) {
            return callable.call();
        }
    }

    public BlockTimer time() {
        return new BlockTimer(this);
    }

    public static class BlockTimer implements AutoCloseable
    {
        private final long start;
        private SparseTimeStat sparseTimeStat;

        public BlockTimer(SparseTimeStat sparseTimeStat)
        {
            this.sparseTimeStat = sparseTimeStat;
            start = sparseTimeStat.ticker.read();
        }

        public void timeTo(SparseTimeStat sparseTimeStat)
        {
            checkArgument(sparseTimeStat.ticker == this.sparseTimeStat.ticker, "sparseTimeStat uses the same ticker");
            this.sparseTimeStat = sparseTimeStat;
        }

        @Override
        public void close()
        {
            sparseTimeStat.add(sparseTimeStat.ticker.read() - start);
        }
    }

    @Override
    protected final Distribution createBucket()
    {
        return new Distribution();
    }

    protected static class Distribution
    {
        private final static double MAX_ERROR = 0.01;
    
        @GuardedBy("this")
        private final QuantileDigest digest;

        @GuardedBy("this")
        private long total = 0;
    
        public Distribution()
        {
            digest = new QuantileDigest(MAX_ERROR);
        }
    
        @Reported
        public synchronized double getCount()
        {
            double count = digest.getCount();
            if (count == 0.0) {
                return Double.NaN;
            }
            return count;
        }

        @Reported
        public synchronized double getTotal() {
            if (digest.getCount() == 0.0) {
                return Double.NaN;
            }
            return convertToSeconds(total);
        }
    
        @Reported
        public synchronized double getP50()
        {
            return convertToSeconds(digest.getQuantile(0.5));
        }
    
        @Reported
        public synchronized double getP75()
        {
            return convertToSeconds(digest.getQuantile(0.75));
        }
    
        @Reported
        public synchronized double getP90()
        {
            return convertToSeconds(digest.getQuantile(0.90));
        }
    
        @Reported
        public synchronized double getP95()
        {
            return convertToSeconds(digest.getQuantile(0.95));
        }
    
        @Reported
        public synchronized double getP99()
        {
            return convertToSeconds(digest.getQuantile(0.99));
        }
    
        @Reported
        public synchronized double getMin()
        {
            return convertToSeconds(digest.getMin());
        }
    
        @Reported
        public synchronized double getMax()
        {
            return convertToSeconds(digest.getMax());
        }

        private static double convertToSeconds(long nanos)
        {
            if (nanos == Long.MAX_VALUE || nanos == Long.MIN_VALUE) {
                return Double.NaN;
            }
            return nanos * 0.000_000_001;
        }
    }
}
