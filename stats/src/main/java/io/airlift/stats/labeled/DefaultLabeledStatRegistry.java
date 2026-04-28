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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class DefaultLabeledStatRegistry
        implements LabeledStatRegistry
{
    private final MBeanExporter exporter;
    private final int labeledStatMaxCardinality;
    private final ConcurrentHashMap<String, AbstractLabeledStat<?>> stats = new ConcurrentHashMap<>();

    public DefaultLabeledStatRegistry(MBeanExporter mBeanExporter, int labeledStatMaxCardinality)
    {
        this.exporter = requireNonNull(mBeanExporter, "mBeanExporter is null");
        checkArgument(labeledStatMaxCardinality > 0, "labeledStatMaxCardinality must be positive");
        this.labeledStatMaxCardinality = labeledStatMaxCardinality;
    }

    @Override
    public MBeanExporter getMBeanExporter()
    {
        return exporter;
    }

    @Override
    public int getLabeledStatMaxCardinality()
    {
        return labeledStatMaxCardinality;
    }

    @Override
    public LabeledCounterStat labeledCounter(String metricName, String description)
    {
        return getOrCreateLabeledStat(metricName, LabeledCounterStat.class, name -> new LabeledCounterStat(this, name, description));
    }

    @Override
    public LabeledGaugeStat labeledGauge(String metricName, String description)
    {
        return getOrCreateLabeledStat(metricName, LabeledGaugeStat.class, name -> new LabeledGaugeStat(this, name, description));
    }

    @Override
    public LabeledHistogramStat labeledHistogram(String metricName, String description, double[] buckets)
    {
        return getOrCreateLabeledStat(metricName, LabeledHistogramStat.class, name -> new LabeledHistogramStat(this, name, description, buckets));
    }

    private <T extends AbstractLabeledStat<?>> T getOrCreateLabeledStat(String metricName, Class<T> type, Function<String, T> factory)
    {
        AbstractLabeledStat<?> existing = stats.computeIfAbsent(metricName, factory);
        if (!type.isInstance(existing)) {
            throw new IllegalArgumentException(String.format(
                    "Metric %s already registered as %s, cannot register as %s",
                    metricName,
                    existing.getClass().getSimpleName(),
                    type.getSimpleName()));
        }
        return type.cast(existing);
    }
}
