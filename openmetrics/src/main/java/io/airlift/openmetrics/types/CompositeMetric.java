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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        extractMetrics(value, metricName, labels, help, subMetrics);
        return new CompositeMetric(metricName, labels, help, subMetrics.build());
    }

    private static void extractMetrics(Object value, String prefix, Map<String, String> labels, String help, ImmutableList.Builder<Metric> metrics)
    {
        switch (value) {
            case Number number -> metrics.add(new Gauge(prefix, number.doubleValue(), labels, help));
            case Boolean bool -> metrics.add(new Gauge(prefix, bool ? 1.0 : 0.0, labels, help));
            case CompositeData compositeData -> {
                CompositeType compositeType = compositeData.getCompositeType();
                for (String itemName : compositeType.keySet()) {
                    extractMetrics(compositeData.get(itemName), prefix + "_" + itemName, labels, help, metrics);
                }
            }
            case TabularData tabularData when !tabularData.isEmpty() -> {
                TabularType tabularType = tabularData.getTabularType();
                Set<String> indexNames = ImmutableSet.copyOf(tabularType.getIndexNames());

                for (Object entry : tabularData.values()) {
                    if (entry instanceof CompositeData compositeData) {
                        Map<String, String> rowLabels = new HashMap<>(labels);
                        for (String indexName : indexNames) {
                            if (compositeData.containsKey(indexName)) {
                                rowLabels.put(indexName, compositeData.get(indexName).toString());
                            }
                        }
                        for (String itemName : Sets.difference(compositeData.getCompositeType().keySet(), indexNames)) {
                            extractMetrics(compositeData.get(itemName), prefix + "_" + itemName, rowLabels, help, metrics);
                        }
                    }
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + value);
        }
    }

    @Override
    public String getMetricExposition()
    {
        throw new UnsupportedOperationException("CompositeMetric container does not support metric exposition, use getMetricExposition on subMetrics instead");
    }

    @Override
    public String getMetricDescriptor()
    {
        throw new UnsupportedOperationException("CompositeMetric container does not support metric descriptor, use getMetricDescriptor on subMetrics instead");
    }
}
