/*
 * Copyright 2015 Proofpoint, Inc.
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

import com.proofpoint.reporting.Bucketed;
import com.proofpoint.reporting.Reported;
import com.proofpoint.stats.MaxGauge.Bucket;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports the maximum (over the minute bucket) of an updated metric
 */
public class MaxGauge
        extends Bucketed<Bucket>
{
    AtomicLong currentValue = new AtomicLong();

    /**
     * Update the current value of the metric.
     *
     * @param value The new value of the metric. Will be considered in
     * subsequent minutes until changed again. This method is intended for
     * measurements, such as the depth of a queue, that persist over time.
     */
    public void update(long value)
    {
        currentValue.set(value);
        applyToCurrentBucket(bucket -> bucket.maxValue.accumulateAndGet(value, Long::max));
    }

    /**
     * Apply an instantaneous value to the maximum, but do not change the current value.
     *
     * @param value The new value of the metric. This method is intended for
     * measurements, such as the size of a request, that should not be
     * considered in subsequent minutes.
     */
    public void updateInstantaneous(long value)
    {
        applyToCurrentBucket(bucket -> bucket.maxValue.accumulateAndGet(value, Long::max));
    }

    /**
     * Add to the current value of the metric.
     *
     * @param delta The amount to add to the current value of the metric.
     */
    public void add(int delta)
    {
        long value = currentValue.addAndGet(delta);
        if (delta > 0) {
            applyToCurrentBucket(bucket -> bucket.maxValue.accumulateAndGet(value, Long::max));
        }
    }

    public long get()
    {
        return currentValue.get();
    }

    @Override
    protected Bucket createBucket()
    {
        return new Bucket(currentValue.get());
    }

    static class Bucket
    {
        AtomicLong maxValue;

        Bucket(long value)
        {
            this.maxValue = new AtomicLong(value);
        }

        @Reported
        public long getMax()
        {
            return maxValue.get();
        }
    }
}
