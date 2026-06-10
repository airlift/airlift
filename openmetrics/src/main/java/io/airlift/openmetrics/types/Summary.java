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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import static io.airlift.openmetrics.MetricsUtils.renderLabels;
import static io.airlift.openmetrics.MetricsUtils.writeSingleMetricDescriptor;
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
    public void writeMetricExposition(Writer writer)
            throws IOException
    {
        String renderedLabels = renderLabels(labels);

        if (count != null) {
            writeValue(writer, "_count", renderedLabels, count.toString());
        }

        if (sum != null) {
            writeValue(writer, "_sum", renderedLabels, sum.toString());
        }

        if (created != null) {
            writeValue(writer, "_created", renderedLabels, created.toString());
        }

        if (quantiles != null) {
            for (Map.Entry<Double, Double> quantile : quantiles.entrySet()) {
                writer.write(metricName);
                if (renderedLabels.isEmpty()) {
                    writer.write("{quantile=\"");
                }
                else {
                    // splice the quantile label into the rendered labels, before the closing brace
                    writer.write(renderedLabels, 0, renderedLabels.length() - 1);
                    writer.write(",quantile=\"");
                }
                writer.write(quantile.getKey().toString());
                writer.write("\"} ");
                writer.write(quantile.getValue().toString());
                writer.write('\n');
            }
        }
    }

    private void writeValue(Writer writer, String suffix, String renderedLabels, String value)
            throws IOException
    {
        writer.write(metricName);
        writer.write(suffix);
        if (!renderedLabels.isEmpty()) {
            writer.write(renderedLabels);
        }
        writer.write(' ');
        writer.write(value);
        writer.write('\n');
    }

    @Override
    public void writeMetricDescriptor(Writer writer)
            throws IOException
    {
        writeSingleMetricDescriptor(writer, metricName, "summary", help);
    }
}
