/*
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
package io.airlift.stats.labeled;

import com.google.errorprone.annotations.ThreadSafe;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import static com.google.common.base.Preconditions.checkArgument;

@ThreadSafe
public class LabeledHistogramStat
        extends AbstractLabeledStat<LabeledHistogramStat.Value>
{
    private final double[] buckets;

    LabeledHistogramStat(LabeledStatRegistry labeledStatRegistry, String metricName, String description, double[] buckets)
    {
        super(labeledStatRegistry, metricName, description);
        for (int i = 1; i < buckets.length; i++) {
            if (buckets[i] <= buckets[i - 1]) {
                throw new IllegalArgumentException("buckets must be in strictly increasing order");
            }
        }
        this.buckets = buckets.clone();
    }

    @Override
    protected Value createStat(LabelSet labels)
    {
        return new Value(metricName, description, labels, buckets);
    }

    public void observe(LabelSet labels, double value)
    {
        Value histogramValue = getOrCreate(labels);
        if (histogramValue != null) {
            histogramValue.observe(value);
        }
    }

    public void batchObserve(LabelSet labels, double value, long count)
    {
        checkArgument(count >= 0, "count must be non-negative");
        Value histogramValue = getOrCreate(labels);
        if (histogramValue != null) {
            histogramValue.batchObserve(value, count);
        }
    }

    /**
     * Histogram where bucket counts are NOT cumulative, each bucket stores the count that bucket
     * There is implicitly 1 additional bucket, the +INF bucket which contains all observations ever seen, it is derived from the count
     * Does not attempt to detect overflow, similar approach to other metrics systems
     */
    @ThreadSafe
    public static class Value
            extends LabeledStat
    {
        private final double[] bucketBounds;
        private final LongAdder[] bucketCounts;
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();

        public Value(String metricName, String description, LabelSet labels, double[] buckets)
        {
            super(metricName, description, labels);
            this.bucketBounds = buckets.clone();
            this.bucketCounts = new LongAdder[buckets.length];
            for (int i = 0; i < buckets.length; i++) {
                bucketCounts[i] = new LongAdder();
            }
        }

        public void observe(double value)
        {
            for (int i = 0; i < bucketBounds.length; i++) {
                if (value <= bucketBounds[i]) {
                    bucketCounts[i].increment();
                    break;
                }
            }
            count.increment();
            sum.add(value);
        }

        public void batchObserve(double value, long numValues)
        {
            for (int i = 0; i < bucketBounds.length; i++) {
                if (value <= bucketBounds[i]) {
                    bucketCounts[i].add(numValues);
                    break;
                }
            }
            count.add(numValues);
            sum.add(numValues * value);
        }

        public long getCount()
        {
            return count.sum();
        }

        public double getSum()
        {
            return sum.sum();
        }

        /**
         * Do not mutate, avoiding defensive copying for performance
         * TODO: expose safer API
         */
        public double[] getBucketBounds()
        {
            return bucketBounds;
        }

        /**
         * Do not mutate, avoiding defensive copying for performance
         * TODO: expose safer API
         */
        public LongAdder[] getBucketCounts()
        {
            return bucketCounts;
        }
    }
}
