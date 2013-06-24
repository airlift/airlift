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

import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TimedStat
{
    private static final double MAX_ERROR = 0.01;
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);
    private final QuantileDigest digest = new QuantileDigest(MAX_ERROR, 0.015);

    @Managed
    public long getCount()
    {
        return count.get();
    }

    @Managed
    public double getSum()
    {
        return sum.get();
    }

    @Managed
    public double getMin()
    {
        return digest.getMin();
    }

    @Managed
    public double getMax()
    {
        return digest.getMax();
    }

    @Managed
    public double getMean()
    {
        long count = this.count.get();
        if (count == 0) {
            return Double.NaN;
        }
        return ((double) sum.get()) / count;
    }

    @Managed
    public double getPercentile(double percentile)
    {
        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        return digest.getQuantile(percentile);
    }

    @Managed(description = "50th Percentile Measurement")
    public double getTP50()
    {
        return digest.getQuantile(0.5);
    }

    @Managed(description = "90th Percentile Measurement")
    public double getTP90()
    {
        return digest.getQuantile(0.9);
    }

    @Managed(description = "99th Percentile Measurement")
    public double getTP99()
    {
        return digest.getQuantile(0.99);
    }

    @Managed(description = "99.9th Percentile Measurement")
    public double getTP999()
    {
        return digest.getQuantile(0.999);
    }

    public void addValue(double value, TimeUnit timeUnit)
    {
        addValue(new Duration(value, timeUnit));
    }

    public void addValue(Duration duration)
    {
        digest.add((long) duration.toMillis());
        sum.addAndGet((long) duration.toMillis());
        count.incrementAndGet();
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        long start = System.nanoTime();
        T result = callable.call();
        addValue(Duration.nanosSince(start));
        return result;
    }
}
