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
import io.airlift.stats.TimeStat.BlockTimer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static java.lang.Math.min;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;


public class TestTimeStat
{
    private static final int VALUES = 1000;
    private ManualTicker ticker;

    @BeforeMethod
    public void setup()
    {
        ticker = new ManualTicker();
    }

    @Test
    public void testBasic()
    {
        TimeStat stat = new TimeStat();
        List<Long> values = new ArrayList<>(VALUES);
        for (long i = 0; i < VALUES; i++) {
            values.add(i);
        }
        Collections.shuffle(values);
        for (Long value : values) {
            stat.add(value, TimeUnit.MILLISECONDS);
        }
        Collections.sort(values);

        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getCount(), (double)values.size());
        assertEquals(allTime.getMax(), values.get(values.size() - 1) * 0.001);
        assertEquals(allTime.getMin(), values.get(0) * 0.001);

        assertPercentile("tp50", allTime.getP50(), values, 0.50);
        assertPercentile("tp50", allTime.getP75(), values, 0.75);
        assertPercentile("tp90", allTime.getP90(), values, 0.90);
        assertPercentile("tp99", allTime.getP99(), values, 0.99);
    }

    @Test
    public void testEmpty()
    {
        TimeStat stat = new TimeStat();
        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getMin(), Double.NaN);
        assertEquals(allTime.getMax(), Double.NaN);
        assertEquals(allTime.getP50(), Double.NaN);
        assertEquals(allTime.getP75(), Double.NaN);
        assertEquals(allTime.getP90(), Double.NaN);
        assertEquals(allTime.getP99(), Double.NaN);
    }

    @Test
    public void time()
            throws Exception
    {
        TimeStat stat = new TimeStat(ticker);
        stat.time(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                ticker.advance(10L * 1000 * 1000);
                return null;
            }
        });

        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getCount(), 1.0);
        assertEquals(allTime.getMin(), 0.010);
        assertEquals(allTime.getMax(), 0.010);
    }

    @Test
    public void timeTry()
            throws Exception
    {
        TimeStat stat = new TimeStat(ticker);
        try (BlockTimer ignored = stat.time()) {
            ticker.advance(10L * 1000 * 1000);
        }

        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getCount(), 1.0);
        assertEquals(allTime.getMin(), 0.010);
        assertEquals(allTime.getMax(), 0.010);
    }

    private void assertPercentile(String name, double value, List<Long> values, double percentile)
    {
        int index = (int) (values.size() * percentile);
        assertBounded(name, value, values.get(index - 1) * 0.001, values.get(min(index + 1, values.size() - 1)) * 0.001);
    }

    private void assertBounded(String name, double value, double minValue, double maxValue)
    {
        if (value >= minValue && value <= maxValue) {
            return;
        }

        fail(String.format("%s expected:<%s> to be between <%s> and <%s>", name, value, minValue, maxValue));
    }

    private static class ManualTicker extends Ticker
    {
        private long nanos = Integer.MAX_VALUE * 2L;

        @Override
        public long read()
        {
            return nanos;
        }

        public void advance(long amount)
        {
            nanos += amount;
        }
    }
}