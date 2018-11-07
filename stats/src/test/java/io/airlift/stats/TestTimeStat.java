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

import io.airlift.stats.TimeStat.BlockTimer;
import io.airlift.testing.TestingTicker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.math.DoubleMath.fuzzyEquals;
import static java.lang.Math.min;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestTimeStat
{
    private static final int VALUES = 1000;
    private TestingTicker ticker;

    @BeforeMethod
    public void setup()
    {
        ticker = new TestingTicker();
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
        assertEquals(allTime.getCount(), (double) values.size());
        assertTrue(fuzzyEquals(allTime.getMax(), values.get(values.size() - 1) * 0.001, 0.000_000_000_1));
        assertEquals(allTime.getMin(), values.get(0) * 0.001);
        assertEquals(allTime.getAvg(), values.stream().mapToDouble(x -> x).average().getAsDouble() * 0.001, 0.001);
        assertEquals(allTime.getUnit(), TimeUnit.SECONDS);

        assertPercentile("tp50", allTime.getP50(), values, 0.50);
        assertPercentile("tp75", allTime.getP75(), values, 0.75);
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
        assertEquals(allTime.getAvg(), Double.NaN);
    }

    @Test
    public void time()
            throws Exception
    {
        TimeStat stat = new TimeStat(ticker);
        stat.time(() -> {
            ticker.increment(10, TimeUnit.MILLISECONDS);
            return null;
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
            ticker.increment(10, TimeUnit.MILLISECONDS);
        }

        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getCount(), 1.0);
        assertEquals(allTime.getMin(), 0.010);
        assertEquals(allTime.getMax(), 0.010);
    }

    @Test
    public void testUnit()
    {
        TimeStat stat = new TimeStat(ticker, TimeUnit.MILLISECONDS);
        stat.add(1, TimeUnit.SECONDS);

        TimeDistribution allTime = stat.getAllTime();
        assertEquals(allTime.getMin(), 1000.0);
        assertEquals(allTime.getMax(), 1000.0);
    }

    private static void assertPercentile(String name, double value, List<Long> values, double percentile)
    {
        int index = (int) (values.size() * percentile);
        assertBounded(name, value, values.get(index - 1) * 0.001, values.get(min(index + 1, values.size() - 1)) * 0.001);
    }

    private static void assertBounded(String name, double value, double minValue, double maxValue)
    {
        if (value >= minValue && value <= maxValue) {
            return;
        }

        fail(String.format("%s expected:<%s> to be between <%s> and <%s>", name, value, minValue, maxValue));
    }
}
