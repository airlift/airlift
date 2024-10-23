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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("deprecation")
public class TimedStatTest
{
    private static final int VALUES = 1000;

    @Test
    public void testBasic()
    {
        TimedStat stat = new TimedStat();
        List<Double> values = new ArrayList<>(VALUES);
        for (int i = 0; i < VALUES; i++) {
            values.add((double) i);
        }
        Collections.shuffle(values);
        for (Double value : values) {
            stat.addValue(value, TimeUnit.MILLISECONDS);
        }
        Collections.sort(values);

        assertThat(stat.getCount()).isEqualTo(values.size());
        assertThat(stat.getMax()).isEqualTo(values.get(values.size() - 1));
        assertThat(stat.getMin()).isEqualTo(values.get(0));
        assertThat(stat.getMean()).isEqualTo((values.get(0) + values.get(values.size() - 1)) / 2.0);

        assertPercentile("tp50", stat.getTP50(), values, 0.50);
        assertPercentile("tp90", stat.getTP90(), values, 0.90);
        assertPercentile("tp99", stat.getTP99(), values, 0.99);
        assertPercentile("tp999", stat.getTP999(), values, 0.999);

        assertPercentile("tp80", stat.getPercentile(0.80), values, 0.80);
        assertPercentile("tp20", stat.getPercentile(0.20), values, 0.20);
    }

    @Test
    public void testEmpty()
    {
        TimedStat stat = new TimedStat();
        assertThat(Double.isNaN(stat.getMin())).isTrue();
        assertThat(Double.isNaN(stat.getMax())).isTrue();
        assertThat(Double.isNaN(stat.getTP50())).isTrue();
        assertThat(Double.isNaN(stat.getTP90())).isTrue();
        assertThat(Double.isNaN(stat.getTP99())).isTrue();
        assertThat(Double.isNaN(stat.getTP999())).isTrue();
        assertThat(Double.isNaN(stat.getPercentile(0.80))).isTrue();
        assertThat(Double.isNaN(stat.getPercentile(0.20))).isTrue();
    }

    @Test
    public void time()
            throws Exception
    {
        TimedStat stat = new TimedStat();
        stat.time((Callable<Void>) () -> {
            LockSupport.parkNanos(SECONDS.toNanos(10));
            return null;
        });

        assertThat(stat.getCount()).isEqualTo(1);
        assertThat(stat.getMin()).isEqualTo(stat.getMax());
        assertThat(stat.getMax()).isGreaterThanOrEqualTo(10.0);
    }

    @Test
    public void illegalParameters()
    {
        TimedStat stat = new TimedStat();
        try {
            stat.getPercentile(-1);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }
        try {
            stat.getPercentile(1.0001);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }
    }

    private void assertPercentile(String name, double value, List<Double> values, double percentile)
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
