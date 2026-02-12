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

import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestStatValidation
{
    private static final LabeledStatRegistry LABELED_STAT_REGISTRY = new LabeledStatRegistry() {
        @Override
        public MBeanExporter getMBeanExporter()
        {
            return null;
        }

        @Override
        public int getLabeledStatMaxCardinality()
        {
            return Integer.MAX_VALUE;
        }
    };

    @Test
    public void testValidateMetricName()
    {
        assertThat(
                assertThrows(IllegalArgumentException.class, () -> new LabeledCounterStat(
                        LABELED_STAT_REGISTRY,
                        "white space",
                        "description")))
                .hasMessage("Invalid metric name: white space, must match regex [a-zA-Z_:][a-zA-Z0-9_:.]*");

        assertThat(
                assertThrows(IllegalArgumentException.class, () -> new LabeledCounterStat(
                        LABELED_STAT_REGISTRY,
                        "11startsWithDigit",
                        "description")))
                .hasMessage("Invalid metric name: 11startsWithDigit, must match regex [a-zA-Z_:][a-zA-Z0-9_:.]*");

        assertThat(
                assertThrows(IllegalArgumentException.class, () -> new LabeledHistogramStat(
                        LABELED_STAT_REGISTRY,
                        "\uD83D\uDE00Name",
                        "description",
                        new double[] {0, 1, 2, 5})))
                .hasMessage("Invalid metric name: \uD83D\uDE00Name, must match regex [a-zA-Z_:][a-zA-Z0-9_:.]*");
    }
}
