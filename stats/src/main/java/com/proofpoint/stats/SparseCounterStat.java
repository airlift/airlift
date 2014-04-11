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

public class SparseCounterStat
    extends Bucketed<SparseCounterStat.Counter>
{
    public void add(long count)
    {
        addInternal((double) count);
    }

    public void add(double count) {
        addInternal(count);
    }

    private void addInternal(final double count)
    {
        applyToCurrentBucket(new Function<Counter, Void>()
        {
            @Override
            public Void apply(Counter input)
            {
                input.count += count;
                return null;
            }
        });
    }

    @Override
    protected Counter createBucket()
    {
        return new Counter();
    }

    protected static class Counter
    {
        private double count = 0;

        @Reported
        public Double getCount()
        {
            if (count == 0.0) {
                return null;
            }
            return count;
        }
    }
}
