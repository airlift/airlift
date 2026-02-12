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
import io.airlift.stats.labeled.LabelSet;
import io.airlift.stats.labeled.LabeledHistogramStat;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Objects.requireNonNull;

public record Histogram(
        String metricName,
        long count,
        double sum,
        OptionalDouble created,
        double[] bucketBounds,
        LongAdder[] bucketCounts,
        LabelSet labels,
        Optional<String> help)
        implements Metric
{
    public Histogram
    {
        requireNonNull(metricName, "metricName is null");
        requireNonNull(created, "created is null");
        requireNonNull(bucketBounds, "bucketBounds is null");
        requireNonNull(bucketCounts, "bucketCounts is null");
        requireNonNull(labels, "labels is null");
        requireNonNull(help, "help is null");

        if (bucketBounds.length != bucketCounts.length) {
            throw new IllegalArgumentException("bucketBounds and bucketCounts must have the same length");
        }
    }

    public static Histogram fromLabeledHistogramStat(LabeledHistogramStat.HistogramStat labeledHistogramStat)
    {
        return new Histogram(
                labeledHistogramStat.metricName(),
                labeledHistogramStat.getCount(),
                labeledHistogramStat.getSum(),
                OptionalDouble.empty(),
                labeledHistogramStat.getBucketBounds(),
                labeledHistogramStat.getBucketCounts(),
                labeledHistogramStat.labels(),
                Optional.ofNullable(labeledHistogramStat.description()));
    }

    @Override
    public String getMetricExposition(boolean includeDescriptor)
    {
        StringBuilder sb = new StringBuilder();

        if (includeDescriptor) {
            sb.append(TYPE_LINE_FORMAT.formatted(metricName, "histogram"));

            if (help.isPresent() && !help.orElseThrow().isEmpty()) {
                sb.append(HELP_LINE_FORMAT.formatted(metricName, help.orElseThrow()));
            }
        }

        // TODO: do not need to regenerate common labels for each bucket, can generate once and reuse if allocation is a concern
        String metricBucketPrefix = metricName + "_bucket";
        long cumulativeCount = 0;
        for (int i = 0; i < bucketBounds.length; i++) {
            cumulativeCount += bucketCounts[i].sum();
            ImmutableMap<String, String> bucketLabels = generateBucketLabels(String.valueOf(bucketBounds[i]));
            sb.append(VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricBucketPrefix, bucketLabels), cumulativeCount));
        }
        sb.append(VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricBucketPrefix, generateBucketLabels("+Inf")), count));

        sb.append(VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_count", labels.asMap()), count));
        sb.append(VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_sum", labels.asMap()), sum));

        if (created.isPresent()) {
            sb.append(VALUE_LINE_FORMAT.formatted(Metric.formatNameWithLabels(metricName + "_created", labels.asMap()), created.orElseThrow()));
        }

        return sb.toString();
    }

    private ImmutableMap<String, String> generateBucketLabels(String bucketBound)
    {
        return ImmutableMap.<String, String>builder()
                .putAll(labels.asMap())
                .put("le", bucketBound)
                .buildOrThrow();
    }
}
