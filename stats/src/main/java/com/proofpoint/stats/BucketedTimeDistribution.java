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
import com.proofpoint.reporting.Bucketed;
import com.proofpoint.reporting.Reported;

import javax.annotation.concurrent.GuardedBy;

public final class BucketedTimeDistribution
    extends Bucketed<BucketedTimeDistribution.Distribution>
{
    public void add(final long value)
    {
        applyToCurrentBucket(new Function<Distribution, Void>()
        {
            @Override
            public Void apply(Distribution input)
            {
                synchronized (input) {
                    input.digest.add(value);
                }
                return null;
            }
        });
    }

    @Override
    protected Distribution createBucket()
    {
        return new Distribution();
    }

    protected static class Distribution
    {
        private final static double MAX_ERROR = 0.01;
    
        @GuardedBy("this")
        private final QuantileDigest digest;
    
        public Distribution()
        {
            digest = new QuantileDigest(MAX_ERROR);
        }
    
        @Reported
        public synchronized double getMaxError()
        {
            return digest.getConfidenceFactor();
        }
    
        @Reported
        public synchronized double getCount()
        {
            return digest.getCount();
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
