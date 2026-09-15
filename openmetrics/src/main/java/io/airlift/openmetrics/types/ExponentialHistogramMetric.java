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
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;

import java.io.Writer;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record ExponentialHistogramMetric(String metricName, ExponentialHistogramSnapshot snapshot, Map<String, String> labels, String help, String unit)
        implements Metric
{
    public static final int MIN_PROMETHEUS_SCALE = -4;
    public static final int MAX_PROMETHEUS_SCALE = 8;

    public ExponentialHistogramMetric
    {
        requireNonNull(metricName, "metricName is null");
        requireNonNull(snapshot, "snapshot is null");
        checkArgument(snapshot.scale() >= MIN_PROMETHEUS_SCALE, "snapshot scale is below the minimum Prometheus scale");
        snapshot = snapshot.downscaleToAtMost(MAX_PROMETHEUS_SCALE);
        labels = ImmutableMap.copyOf(labels);
    }

    @Override
    public void writeMetricExposition(Writer writer)
    {
        throw new UnsupportedOperationException("Exponential histograms require Prometheus protobuf exposition");
    }

    @Override
    public void writeMetricDescriptor(Writer writer)
    {
        throw new UnsupportedOperationException("Exponential histograms require Prometheus protobuf exposition");
    }
}
