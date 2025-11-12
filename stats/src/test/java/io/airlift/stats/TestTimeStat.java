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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.math.DoubleMath.fuzzyEquals;
import static io.airlift.stats.TimeDistribution.MERGE_THRESHOLD_NANOS;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestTimeStat
{
    private static final int VALUES = 1000;
    private TestingTicker ticker;

    @BeforeEach
    public void setup()
    {
        ticker = new TestingTicker();
    }

    @Test
    public void testBasic()
    {
        TimeStat stat = new TimeStat(ticker);
        List<Long> values = new ArrayList<>(VALUES);
        for (long i = 0; i < VALUES; i++) {
            values.add(i);
        }
        Collections.shuffle(values);
        for (Long value : values) {
            stat.add(value, TimeUnit.MILLISECONDS);
        }
        Collections.sort(values);

        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        TimeDistribution allTime = stat.getAllTime();
        assertThat(allTime.getCount()).isEqualTo(values.size());
        assertThat(fuzzyEquals(allTime.getMax(), values.getLast() * 0.001, 0.000_000_000_1)).isTrue();
        assertThat(allTime.getMin()).isEqualTo(values.getFirst() * 0.001);
        assertThat(allTime.getAvg()).isCloseTo(values.stream().mapToDouble(x -> x).average().getAsDouble() * 0.001, within(0.001));
        assertThat(allTime.getUnit()).isEqualTo(TimeUnit.SECONDS);

        assertPercentile("tp50", allTime.getP50(), values, 0.50);
        assertPercentile("tp75", allTime.getP75(), values, 0.75);
        assertPercentile("tp90", allTime.getP90(), values, 0.90);
        assertPercentile("tp99", allTime.getP99(), values, 0.99);
    }

    @Test
    public void testAddIllegalDoubles()
    {
        TimeStat stat = new TimeStat(ticker);

        assertThatThrownBy(() -> stat.add(-1.0, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat.add(Double.NaN, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat.add(Double.POSITIVE_INFINITY, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat.add(Double.NEGATIVE_INFINITY, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stat.add(1.0d / 0.0d, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class);

        stat.add(0.0, TimeUnit.MILLISECONDS); // 0.0 is valid
        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        assertThat(stat.getAllTime().getCount()).isEqualTo(1.0);
        assertThat(stat.getAllTime().getMin()).isEqualTo(0.0);
        assertThat(stat.getAllTime().getMax()).isEqualTo(0.0);
    }

    @Test
    public void testReset()
    {
        TimeStat stat = new TimeStat(ticker);
        for (long value = 0; value < VALUES; value++) {
            stat.add(value, TimeUnit.MILLISECONDS);
        }

        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        assertThat(stat.getAllTime().getCount()).isEqualTo(VALUES);
        assertThat(stat.getOneMinute().getCount()).isEqualTo(VALUES);
        assertThat(stat.getFiveMinutes().getCount()).isEqualTo(VALUES);
        assertThat(stat.getFifteenMinutes().getCount()).isEqualTo(VALUES);

        assertThat(stat.getAllTime().getAvg()).isNotNaN();
        assertThat(stat.getOneMinute().getAvg()).isNotNaN();
        assertThat(stat.getFiveMinutes().getAvg()).isNotNaN();
        assertThat(stat.getFifteenMinutes().getAvg()).isNotNaN();

        stat.reset();

        assertThat(stat.getAllTime().getCount()).isEqualTo(0D);
        assertThat(stat.getOneMinute().getCount()).isEqualTo(0D);
        assertThat(stat.getFiveMinutes().getCount()).isEqualTo(0D);
        assertThat(stat.getFifteenMinutes().getCount()).isEqualTo(0D);

        assertThat(stat.getAllTime().getAvg()).isNaN();
        assertThat(stat.getOneMinute().getAvg()).isNaN();
        assertThat(stat.getFiveMinutes().getAvg()).isNaN();
        assertThat(stat.getFifteenMinutes().getAvg()).isNaN();
    }

    @Test
    public void testEmpty()
    {
        TimeStat stat = new TimeStat(ticker);
        TimeDistribution allTime = stat.getAllTime();
        assertThat(allTime.getMin()).isNaN();
        assertThat(allTime.getMax()).isNaN();
        assertThat(allTime.getP50()).isNaN();
        assertThat(allTime.getP75()).isNaN();
        assertThat(allTime.getP90()).isNaN();
        assertThat(allTime.getP99()).isNaN();
        assertThat(allTime.getAvg()).isNaN();
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

        try {
            stat.time(() -> {
                ticker.increment(20, TimeUnit.MILLISECONDS);
                throw new Exception("thrown by time");
            });
            fail("Exception should have been thrown");
        }
        catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("thrown by time");
        }

        TimeDistribution allTime = stat.getAllTime();
        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        assertThat(allTime.getCount()).isEqualTo(2.0);
        assertThat(allTime.getMin()).isEqualTo(0.010);
        assertThat(allTime.getMax()).isEqualTo(0.020);
    }

    @Test
    public void timeTry()
    {
        TimeStat stat = new TimeStat(ticker);
        try (BlockTimer ignored = stat.time()) {
            ticker.increment(10, TimeUnit.MILLISECONDS);
        }

        TimeDistribution allTime = stat.getAllTime();
        ticker.increment(MERGE_THRESHOLD_NANOS - 10, TimeUnit.NANOSECONDS); // force a merge
        assertThat(allTime.getCount()).isEqualTo(1.0);
        assertThat(allTime.getMin()).isEqualTo(0.010);
        assertThat(allTime.getMax()).isEqualTo(0.010);
    }

    @Test
    public void testUnit()
    {
        TimeStat stat = new TimeStat(ticker, TimeUnit.MILLISECONDS);
        stat.add(1, TimeUnit.SECONDS);

        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        TimeDistribution allTime = stat.getAllTime();
        assertThat(allTime.getMin()).isEqualTo(1000.0);
        assertThat(allTime.getMax()).isEqualTo(1000.0);
    }

    @Test
    public void testAddNanos()
    {
        TimeStat stat = new TimeStat(ticker, TimeUnit.NANOSECONDS);
        stat.add(1, TimeUnit.MILLISECONDS);
        stat.addNanos(1L);

        ticker.increment(MERGE_THRESHOLD_NANOS, TimeUnit.NANOSECONDS); // force a merge
        TimeDistribution allTime = stat.getAllTime();
        assertThat(allTime.getMin()).isEqualTo(1.0);
        assertThat(allTime.getMax()).isEqualTo(1000000.0);
        assertThat(allTime.getCount()).isEqualTo(2.0);

        assertThatThrownBy(() -> stat.addNanos(-1))
                .isInstanceOf(IllegalArgumentException.class);
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
