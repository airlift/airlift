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

import com.proofpoint.stats.TimerStat.BlockTimer;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.proofpoint.testing.Assertions.assertGreaterThanOrEqual;
import static java.lang.Math.min;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;


public class TestTimerStat
{
    private static final int VALUES = 1000;

    @Test
    public void testBasic()
    {
        TimerStat stat = new TimerStat();
        List<Long> values = new ArrayList<>(VALUES);
        for (long i = 0; i < VALUES; i++) {
            values.add(i);
        }
        Collections.shuffle(values);
        for (Long value : values) {
            stat.addValue(value, TimeUnit.MILLISECONDS);
        }
        Collections.sort(values);

        Distribution allTime = stat.getDistributionStat().getAllTime();
        assertEquals(allTime.getCount(), (double)values.size());
        assertEquals((Long) allTime.getMax(), values.get(values.size() - 1));
        assertEquals((Long)allTime.getMin(), values.get(0));

        assertPercentile("tp50", allTime.getP50(), values, 0.50);
        assertPercentile("tp50", allTime.getP75(), values, 0.75);
        assertPercentile("tp90", allTime.getP90(), values, 0.90);
        assertPercentile("tp99", allTime.getP99(), values, 0.99);
    }

    @Test
    public void testEmpty()
    {
        TimerStat stat = new TimerStat();
        Distribution allTime = stat.getDistributionStat().getAllTime();
        assertEquals(allTime.getMin(), Long.MAX_VALUE);
        assertEquals(allTime.getMax(), Long.MIN_VALUE);
        assertEquals(allTime.getP50(), Long.MIN_VALUE);
        assertEquals(allTime.getP75(), Long.MIN_VALUE);
        assertEquals(allTime.getP90(), Long.MIN_VALUE);
        assertEquals(allTime.getP99(), Long.MIN_VALUE);
    }

    @Test
    public void time()
            throws Exception
    {
        TimerStat stat = new TimerStat();
        stat.time(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                LockSupport.parkNanos(10L * 1000 * 1000);
                return null;
            }
        });

        Distribution allTime = stat.getDistributionStat().getAllTime();
        assertEquals(allTime.getCount(), 1.0);
        assertEquals(allTime.getMin(), allTime.getMax());
        assertGreaterThanOrEqual(allTime.getMax(), 10L);
    }

    @Test
    public void timeTry()
            throws Exception
    {
        TimerStat stat = new TimerStat();
        try (BlockTimer ignored = stat.time()) {
            LockSupport.parkNanos(10L * 1000 * 1000);
        }

        Distribution allTime = stat.getDistributionStat().getAllTime();
        assertEquals(allTime.getCount(), 1.0);
        assertEquals(allTime.getMin(), allTime.getMax());
        assertGreaterThanOrEqual(allTime.getMax(), 10L);
    }

    private void assertPercentile(String name, double value, List<Long> values, double percentile)
    {
        int index = (int) (values.size() * percentile);
        assertBounded(name, value, values.get(index - 1), values.get(min(index + 1, values.size() - 1)));
    }

    private void assertBounded(String name, double value, double minValue, double maxValue)
    {
        if (value >= minValue && value <= maxValue) {
            return;
        }

        fail(String.format("%s expected:<%s> to be between <%s> and <%s>", name, value, minValue, maxValue));
    }
}