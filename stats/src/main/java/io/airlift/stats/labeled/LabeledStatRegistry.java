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

import org.weakref.jmx.MBeanExporter;

/**
 * A LabeledStatRegistry is a factory for constructing labeled stats
 * There exists a single implementation GlobalLabeledStatRegistry which is intended to be a singleton per MBeanExporter
 * In future could create several registry implementations with different configuration
 */
public interface LabeledStatRegistry
{
    MBeanExporter getMBeanExporter();

    int getLabeledStatMaxCardinality();

    default LabeledCounterStat labeledCounter(String metricName, String description)
    {
        return new LabeledCounterStat(this, metricName, description);
    }

    default LabeledGaugeStat labeledGauge(String metricName, String description)
    {
        return new LabeledGaugeStat(this, metricName, description);
    }

    default LabeledHistogramStat labeledHistogram(String metricName, String description, double[] buckets)
    {
        return new LabeledHistogramStat(this, metricName, description, buckets);
    }
}
