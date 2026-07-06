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
package io.airlift.openmetrics.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.metrics.CompositeDataFlattener;

import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public record CompositeMetric(String metricName, Map<String, String> labels, String help, List<Metric> subMetrics)
        implements Metric
{
    public CompositeMetric
    {
        requireNonNull(metricName, "metricName is null");
        requireNonNull(subMetrics, "subMetrics is null");
        labels = ImmutableMap.copyOf(labels);
    }

    public static CompositeMetric from(String metricName, Object value, Map<String, String> labels, String help)
    {
        requireNonNull(value, "value is null");
        ImmutableList.Builder<Metric> subMetrics = ImmutableList.builder();
        CompositeDataFlattener.flatten(metricName, value, labels, "_", UnaryOperator.identity(), (name, leafLabels, leafValue) -> {
            switch (leafValue) {
                case Number number -> subMetrics.add(new Gauge(name, number.doubleValue(), leafLabels, help));
                case Boolean bool -> subMetrics.add(new Gauge(name, bool ? 1.0 : 0.0, leafLabels, help));
                default -> throw new IllegalStateException("Unexpected value: " + leafValue);
            }
        });
        return new CompositeMetric(metricName, labels, help, subMetrics.build());
    }

    @Override
    public void writeMetricExposition(Writer writer)
    {
        throw new UnsupportedOperationException("CompositeMetric container does not support metric exposition, use writeMetricExposition on subMetrics instead");
    }

    @Override
    public void writeMetricDescriptor(Writer writer)
    {
        throw new UnsupportedOperationException("CompositeMetric container does not support metric descriptor, use writeMetricDescription on subMetrics instead");
    }
}
