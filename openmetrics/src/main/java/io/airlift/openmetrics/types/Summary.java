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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import io.airlift.stats.TimeDistribution;
import java.util.Map;

public record Summary(
        String metricName,
        Long count,
        Double sum,
        Double created,
        Map<Double, Double> quantiles,
        Map<String, String> labels,
        String help)
        implements Metric {
    public static Summary from(
            String metricName, TimeDistribution timeDistribution, Map<String, String> labels, String help) {
        return new Summary(
                metricName,
                (long) timeDistribution.getCount(),
                timeDistribution.getAvg() * timeDistribution.getCount(),
                null,
                ImmutableMap.<Double, Double>builder()
                        .put(0.5, timeDistribution.getP50())
                        .put(0.75, timeDistribution.getP75())
                        .put(0.9, timeDistribution.getP90())
                        .put(0.95, timeDistribution.getP95())
                        .put(0.99, timeDistribution.getP99())
                        .build(),
                labels,
                help);
    }

    public Summary(
            String metricName,
            Long count,
            Double sum,
            Double created,
            Map<Double, Double> quantiles,
            Map<String, String> labels,
            String help) {
        this.metricName = requireNonNull(metricName, "metricName is null");
        this.count = count;
        this.sum = sum;
        this.created = created;
        this.quantiles = quantiles;
        this.labels = labels;
        this.help = help;
    }

    @Override
    public String getMetricExposition() {
        StringBuilder stringBuilder = new StringBuilder(TYPE_LINE_FORMAT.formatted(metricName, "summary"));

        if (help != null && !help.isEmpty()) {
            stringBuilder.append(HELP_LINE_FORMAT.formatted(metricName, help));
        }

        if (count != null) {
            stringBuilder.append(
                    VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_count", labels), count));
        }

        if (sum != null) {
            stringBuilder.append(
                    VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_sum", labels), sum));
        }

        if (created != null) {
            stringBuilder.append(
                    VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_created", labels), created));
        }

        if (quantiles != null) {
            for (Map.Entry<Double, Double> quantile : quantiles.entrySet()) {
                Map<String, String> quantileLabels = new ImmutableMap.Builder<String, String>()
                        .putAll(labels)
                        .put("quantile", String.valueOf(quantile.getKey()))
                        .buildOrThrow();
                stringBuilder.append(VALUE_LINE_FORMAT.formatted(
                        Metric.formatNameWithLabels(metricName, quantileLabels), quantile.getValue()));
            }
        }

        return stringBuilder.toString();
    }
}
