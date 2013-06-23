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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @deprecated Replaced by {@link TimeStat}
 */
@Deprecated
public class TimedStat
{
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);
    private final ExponentiallyDecayingSample sample = new ExponentiallyDecayingSample(1028, 0.015);

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
        List<Long> values = sample.values();
        if (!values.isEmpty()) {
            return Collections.min(values);
        }

        return Double.NaN;
    }

    @Managed
    public double getMax()
    {
        List<Long> values = sample.values();
        if (!values.isEmpty()) {
            return Collections.max(values);
        }

        return Double.NaN;
    }

    @Managed
    public double getMean()
    {
        List<Long> values = sample.values();

        if (!values.isEmpty()) {
            long sum = 0;
            for (long value : values) {
                sum += value;
            }

            return sum * 1.0 / values.size();
        }

        return Double.NaN;
    }

    @Managed
    public double getPercentile(double percentile)
    {
        if (percentile < 0 || percentile > 1) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        return sample.percentiles(percentile)[0];
    }

    @Managed(description = "50th Percentile Measurement")
    public double getTP50()
    {
        return sample.percentiles(0.5)[0];
    }

    @Managed(description = "90th Percentile Measurement")
    public double getTP90()
    {
        return sample.percentiles(0.9)[0];
    }

    @Managed(description = "99th Percentile Measurement")
    public double getTP99()
    {
        return sample.percentiles(0.99)[0];
    }

    @Managed(description = "99.9th Percentile Measurement")
    public double getTP999()
    {
        return sample.percentiles(0.999)[0];
    }

    public void addValue(double value, TimeUnit timeUnit)
    {
        addValue(new Duration(value, timeUnit));
    }

    public void addValue(Duration duration)
    {
        sample.update(duration.toMillis());
        sum.addAndGet(duration.toMillis());
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
