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

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLabeledStatRegistry
{
    private static LabeledStatRegistry newRegistry()
    {
        MBeanServer mBeanServer = MBeanServerFactory.newMBeanServer();
        return new DefaultLabeledStatRegistry(new MBeanExporter(mBeanServer), 100);
    }

    @Test
    public void testCounterDedupedByNameKeepsFirstDescription()
    {
        LabeledStatRegistry registry = newRegistry();
        LabeledCounterStat first = registry.labeledCounter("requests", "first description");
        LabeledCounterStat second = registry.labeledCounter("requests", "second description");
        assertThat(second).isSameAs(first);

        LabelSet labels = LabelSet.fromLabels(ImmutableMap.of("k", "v"));
        second.increment(labels);
        assertThat(first.labeledStats.get(labels).description()).isEqualTo("first description");
    }

    @Test
    public void testGaugeDedupedByNameKeepsFirstDescription()
    {
        LabeledStatRegistry registry = newRegistry();
        LabeledGaugeStat first = registry.labeledGauge("queue_depth", "first description");
        LabeledGaugeStat second = registry.labeledGauge("queue_depth", "second description");
        assertThat(second).isSameAs(first);

        LabelSet labels = LabelSet.fromLabels(ImmutableMap.of("k", "v"));
        second.set(labels, 1.0);
        assertThat(first.labeledStats.get(labels).description()).isEqualTo("first description");
    }

    @Test
    public void testHistogramDedupedByNameKeepsFirstDescriptionAndBuckets()
    {
        LabeledStatRegistry registry = newRegistry();
        double[] firstBuckets = {1.0, 10.0, 100.0};
        double[] secondBuckets = {2.0, 20.0};
        LabeledHistogramStat first = registry.labeledHistogram("latency", "first description", firstBuckets);
        LabeledHistogramStat second = registry.labeledHistogram("latency", "second description", secondBuckets);
        assertThat(second).isSameAs(first);

        LabelSet labels = LabelSet.fromLabels(ImmutableMap.of("k", "v"));
        second.observe(labels, 5.0);
        LabeledHistogramStat.Value value = first.labeledStats.get(labels);
        assertThat(value.description()).isEqualTo("first description");
        assertThat(value.getBucketBounds()).containsExactly(1.0, 10.0, 100.0);
    }

    @Test
    public void testDifferentNamesReturnDifferentInstances()
    {
        LabeledStatRegistry registry = newRegistry();
        LabeledCounterStat a = registry.labeledCounter("a", "");
        LabeledCounterStat b = registry.labeledCounter("b", "");
        assertThat(b).isNotSameAs(a);
    }

    @Test
    public void testCounterNameCollidesWithGauge()
    {
        LabeledStatRegistry registry = newRegistry();
        registry.labeledGauge("shared_name", "");

        assertThatThrownBy(() -> registry.labeledCounter("shared_name", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric shared_name already registered as LabeledGaugeStat, cannot register as LabeledCounterStat");
    }

    @Test
    public void testGaugeNameCollidesWithCounter()
    {
        LabeledStatRegistry registry = newRegistry();
        registry.labeledCounter("shared_name", "");

        assertThatThrownBy(() -> registry.labeledGauge("shared_name", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric shared_name already registered as LabeledCounterStat, cannot register as LabeledGaugeStat");
    }

    @Test
    public void testHistogramNameCollidesWithCounter()
    {
        LabeledStatRegistry registry = newRegistry();
        registry.labeledCounter("shared_name", "");

        assertThatThrownBy(() -> registry.labeledHistogram("shared_name", "", new double[] {1.0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Metric shared_name already registered as LabeledCounterStat, cannot register as LabeledHistogramStat");
    }

    @Test
    public void testIncrementsViaDuplicateReferenceShareChildren()
    {
        LabeledStatRegistry registry = newRegistry();
        LabelSet labels = LabelSet.fromLabels(ImmutableMap.of("endpoint", "/foo"));
        LabeledCounterStat first = registry.labeledCounter("requests", "");
        LabeledCounterStat second = registry.labeledCounter("requests", "");

        first.increment(labels);
        second.increment(labels);

        assertThat(first.labeledStats).hasSize(1);
        assertThat(first.labeledStats.get(labels).getCount()).isEqualTo(2);
    }
}
