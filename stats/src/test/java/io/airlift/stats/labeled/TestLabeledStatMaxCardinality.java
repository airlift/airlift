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

public class TestLabeledStatMaxCardinality
{
    @Test
    public void testCardinalityLimit()
    {
        MBeanServer mBeanServer = MBeanServerFactory.newMBeanServer();
        MBeanExporter mBeanExporter = new MBeanExporter(mBeanServer);
        LabeledStatRegistry labeledStatRegistry = new DefaultLabeledStatRegistry(mBeanExporter, 10);
        LabeledCounterStat counterStat = labeledStatRegistry.labeledCounter("cardinality_test", "for tests");
        for (int i = 0; i < 10; i++) {
            counterStat.increment(LabelSet.fromLabels(ImmutableMap.of("label", "value_" + i)));
            assertThat(counterStat.labeledStats).hasSize(i + 1);
        }

        // After hitting max cardinality should not create new labeled stat values
        assertThat(counterStat.labeledStats).hasSize(10);
        LabelSet tenLabel = LabelSet.fromLabels(ImmutableMap.of("label", "value_10"));
        counterStat.increment(tenLabel);
        assertThat(counterStat.labeledStats.get(tenLabel)).isNull();
        assertThat(counterStat.labeledStats).hasSize(10);

        // But existing label sets should continue to function
        LabelSet oneLabel = LabelSet.fromLabels(ImmutableMap.of("label", "value_1"));
        assertThat(counterStat.labeledStats.get(oneLabel).getCount()).isEqualTo(1);
        counterStat.increment(oneLabel);
        assertThat(counterStat.labeledStats.get(oneLabel).getCount()).isEqualTo(2);
    }
}
