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
package io.airlift.openmetrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.metrics.CollectedMetricGroup;
import io.airlift.metrics.CollectedMetricGroup.Attribute;
import io.airlift.metrics.MetricSource;
import io.airlift.metrics.MetricsCollector;
import io.airlift.metrics.StatWindows;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;

import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

/**
 * Collects the structured OpenMetrics values rendered by {@link MetricsResource}.
 */
public class OpenMetricsCollector
{
    private static final String ATTRIBUTE_SEPARATOR = "_ATTRIBUTE_";
    private static final String TYPE_SEPARATOR = "_TYPE_";
    private static final String NAME_SEPARATOR = "_NAME_";
    private static final CharMatcher NON_ALLOWED_LABEL_CHARACTERS = CharMatcher
            .inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("_"))
            .negate()
            .precomputed();

    private final MetricsCollector collector;

    @Inject
    public OpenMetricsCollector(MetricsCollector collector)
    {
        this.collector = requireNonNull(collector, "collector is null");
    }

    public List<Metric> collect()
    {
        return toOpenMetrics(collector.collect());
    }

    public List<Metric> collect(List<String> filters)
    {
        if (filters == null || filters.isEmpty()) {
            return collect();
        }
        return filterMetrics(toOpenMetrics(collector.collect()), ImmutableSet.copyOf(filters));
    }

    public Optional<Metric> findMetric(String metricName)
    {
        return findMetric(collect(List.of(metricName)), metricName);
    }

    @VisibleForTesting
    static Optional<Metric> findMetric(List<Metric> metrics, String metricName)
    {
        Optional<Metric> metric = metrics.stream()
                .filter(candidate -> candidate.metricName().equals(metricName))
                .findFirst();
        if (metric.isPresent()) {
            return metric;
        }
        return metrics.stream()
                .flatMap(OpenMetricsCollector::flattenMetric)
                .filter(candidate -> candidate.metricName().equals(metricName))
                .findFirst();
    }

    private static java.util.stream.Stream<Metric> flattenMetric(Metric metric)
    {
        if (metric instanceof CompositeMetric compositeMetric) {
            return compositeMetric.subMetrics().stream();
        }
        return java.util.stream.Stream.of(metric);
    }

    @VisibleForTesting
    static List<Metric> toOpenMetrics(List<CollectedMetricGroup> collectedMetricGroups)
    {
        ImmutableList.Builder<Metric> metrics = ImmutableList.builder();
        collectedMetricGroups.stream()
                .flatMap(group -> group.attributes().stream()
                        .map(attribute -> toOpenMetric(openMetricsMetricName(group, attribute), attribute, group.labels())))
                .flatMap(Optional::stream)
                .forEach(metrics::add);
        return metrics.build();
    }

    @VisibleForTesting
    static List<Metric> filterMetrics(List<Metric> metrics, Set<String> filters)
    {
        ImmutableList.Builder<Metric> filtered = ImmutableList.builder();
        for (Metric metric : metrics) {
            if (metric instanceof CompositeMetric compositeMetric) {
                if (filters.contains(compositeMetric.metricName())) {
                    filtered.add(compositeMetric);
                    continue;
                }
                List<Metric> filteredSubMetrics = compositeMetric.subMetrics().stream()
                        .filter(subMetric -> filters.contains(subMetric.metricName()))
                        .collect(toImmutableList());
                if (!filteredSubMetrics.isEmpty()) {
                    filtered.add(new CompositeMetric(compositeMetric.metricName(), compositeMetric.labels(), compositeMetric.help(), filteredSubMetrics));
                }
            }
            else if (filters.contains(metric.metricName())) {
                filtered.add(metric);
            }
        }
        return filtered.build();
    }

    @VisibleForTesting
    static Optional<Metric> toOpenMetric(Attribute attribute, Map<String, String> labels)
    {
        return toOpenMetric(openMetricsMetricName(attribute.path()), attribute, labels);
    }

    private static Optional<Metric> toOpenMetric(String metricName, Attribute attribute, Map<String, String> labels)
    {
        Object value = attribute.value();
        return switch (value) {
            case Number number -> Optional.of(Gauge.from(metricName, number, labels, attribute.description()));
            case Boolean bool -> Optional.of(Gauge.from(metricName, bool ? 1 : 0, labels, attribute.description()));
            case CompositeData compositeData -> Optional.of(CompositeMetric.from(metricName, compositeData, labels, attribute.description()));
            case TabularData tabularData -> Optional.of(CompositeMetric.from(metricName, tabularData, labels, attribute.description()));
            case CounterStat counterStat -> Optional.of(Counter.from(metricName, counterStat, labels, attribute.description()));
            case TimeDistribution timeDistribution -> Optional.of(Summary.from(metricName, timeDistribution, labels, attribute.description()));
            case TimeStat timeStat -> Optional.of(timeStatToOpenMetrics(metricName, timeStat, attribute, labels));
            case Distribution distribution -> Optional.of(Summary.from(metricName, distribution, labels, attribute.description()));
            case DistributionStat distributionStat -> Optional.of(distributionStatToOpenMetrics(metricName, distributionStat, attribute, labels));
            case null, default -> Optional.empty();
        };
    }

    private static CompositeMetric timeStatToOpenMetrics(String metricName, TimeStat timeStat, Attribute attribute, Map<String, String> labels)
    {
        ImmutableList.Builder<Metric> metrics = ImmutableList.builder();
        for (StatWindows.Window<TimeDistribution> window : StatWindows.windows(timeStat)) {
            metrics.add(Summary.from(metricName + "_" + window.name(), window.value(), labels, attribute.description()));
        }
        return new CompositeMetric(metricName, labels, attribute.description(), metrics.build());
    }

    private static CompositeMetric distributionStatToOpenMetrics(String metricName, DistributionStat distributionStat, Attribute attribute, Map<String, String> labels)
    {
        ImmutableList.Builder<Metric> metrics = ImmutableList.builder();
        for (StatWindows.Window<Distribution> window : StatWindows.windows(distributionStat)) {
            metrics.add(Summary.from(metricName + "_" + window.name(), window.value(), labels, attribute.description()));
        }
        return new CompositeMetric(metricName, labels, attribute.description(), metrics.build());
    }

    @VisibleForTesting
    static String sanitizeMetricName(String name)
    {
        return NON_ALLOWED_LABEL_CHARACTERS.collapseFrom(name, '_');
    }

    private static String openMetricsMetricName(CollectedMetricGroup group, Attribute attribute)
    {
        String attributeName = openMetricsMetricName(attribute.path());
        return switch (group.source()) {
            case MetricSource.JmxMetricSource jmxMetricSource -> jmxMetricNamePrefix(jmxMetricSource.name()) + ATTRIBUTE_SEPARATOR + attributeName;
            case MetricSource.ManagedMetricSource managedMetricSource -> {
                String metricName = sanitizeMetricName(managedMetricSource.name());
                if (attributeName.isEmpty()) {
                    yield metricName;
                }
                yield metricName + "_" + attributeName;
            }
        };
    }

    private static String jmxMetricNamePrefix(ObjectName objectName)
    {
        if (objectName.getDomain().contains("_")) {
            throw new RuntimeException("Bad domain name %s".formatted(objectName.getDomain()));
        }

        StringBuilder metricNameBuilder = new StringBuilder("JMX_")
                .append(objectName.getDomain());

        if (objectName.getKeyProperty("name") != null) {
            metricNameBuilder.append(NAME_SEPARATOR)
                    .append(objectName.getKeyProperty("name"));
        }

        if (objectName.getKeyProperty("type") != null) {
            metricNameBuilder.append(TYPE_SEPARATOR)
                    .append(objectName.getKeyProperty("type"));
        }

        return sanitizeMetricName(metricNameBuilder.toString());
    }

    private static String openMetricsMetricName(List<String> path)
    {
        return sanitizeMetricName(String.join("_", path));
    }
}
