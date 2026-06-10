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

import com.google.common.collect.ImmutableMap;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeDistribution.TimeDistributionSnapshot;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record Summary(String metricName, Long count, Double sum, Double created, Map<Double, Double> quantiles, Map<String, String> labels, String help)
        implements Metric
{
    public static Summary from(String metricName, TimeDistribution timeDistribution, Map<String, String> labels, String help)
    {
        // a single snapshot takes the distribution lock once and yields mutually consistent values,
        // unlike calling the individually synchronized getters
        TimeDistributionSnapshot snapshot = timeDistribution.snapshot();
        return new Summary(
                metricName,
                (long) snapshot.count(),
                snapshot.avg() * snapshot.count(),
                null,
                ImmutableMap.<Double, Double>builder()
                        .put(0.5, snapshot.p50())
                        .put(0.75, snapshot.p75())
                        .put(0.9, snapshot.p90())
                        .put(0.95, snapshot.p95())
                        .put(0.99, snapshot.p99())
                        .build(),
                labels,
                help);
    }

    public Summary
    {
        requireNonNull(metricName, "metricName is null");
        labels = ImmutableMap.copyOf(labels);
    }

    @Override
    public String getMetricExposition()
    {
        StringBuilder stringBuilder = new StringBuilder();
        if (count != null) {
            stringBuilder
                    .append(Metric.formatNameWithLabels(metricName + "_count", labels))
                    .append(' ')
                    .append(count)
                    .append('\n');
        }

        if (sum != null) {
            stringBuilder
                    .append(Metric.formatNameWithLabels(metricName + "_sum", labels))
                    .append(' ')
                    .append(sum)
                    .append('\n');
        }

        if (created != null) {
            stringBuilder
                    .append(Metric.formatNameWithLabels(metricName + "_created", labels))
                    .append(' ')
                    .append(created)
                    .append('\n');
        }

        if (quantiles != null) {
            for (Map.Entry<Double, Double> quantile : quantiles.entrySet()) {
                Map<String, String> quantileLabels = new ImmutableMap.Builder<String, String>().putAll(labels)
                        .put("quantile", String.valueOf(quantile.getKey())).buildOrThrow();
                stringBuilder
                        .append(Metric.formatNameWithLabels(metricName, quantileLabels))
                        .append(' ')
                        .append(quantile.getValue())
                        .append('\n');
            }
        }

        return stringBuilder.toString();
    }

    @Override
    public String getMetricDescriptor()
    {
        return Metric.formatMetricDescriptor(metricName, "summary", help);
    }
}
