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
 * Currently exists a single implementation DefaultLabeledStatRegistry which is intended to be a singleton per MBeanExporter
 */
public interface LabeledStatRegistry
{
    MBeanExporter getMBeanExporter();

    int getLabeledStatMaxCardinality();

    LabeledCounterStat labeledCounter(String metricName, String description);

    LabeledGaugeStat labeledGauge(String metricName, String description);

    LabeledHistogramStat labeledHistogram(String metricName, String description, double[] buckets);
}
