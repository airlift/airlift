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

import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TimerStat
{
    private final DistributionStat distributionStat = new DistributionStat();

    @Managed
    @Flatten
    public DistributionStat getDistributionStat()

    {
        return distributionStat;
    }

    public void addValue(double value, TimeUnit timeUnit)
    {
        addValue(new Duration(value, timeUnit));
    }

    public void addValue(Duration duration)
    {
        distributionStat.add((long) duration.toMillis());
    }

    public <T> T time(Callable<T> callable)
            throws Exception
    {
        long start = System.nanoTime();
        T result = callable.call();
        addValue(Duration.nanosSince(start));
        return result;
    }

    public BlockTimer time() {
        return new BlockTimer();
    }

    public class BlockTimer implements AutoCloseable
    {
        private final long start = System.nanoTime();

        @Override
        public void close()
        {
            addValue(Duration.nanosSince(start));
        }
    }
}
