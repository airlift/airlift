package io.airlift.openmetrics;

import com.google.common.base.Strings;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Metric;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class MetricsUtils
{
    private MetricsUtils() {}

    static void writeMetricsExpositions(Writer writer, List<Metric> metrics)
    {
        try {
            writeMetricFamilies(writer, groupMetricFamilies(metrics));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String renderMetricsExpositions(List<Metric> metrics)
    {
        return renderMetricsExpositions(metrics, false);
    }

    static String renderMetricsExpositions(List<Metric> metrics, boolean withEof)
    {
        StringWriter writer = new StringWriter();
        writeMetricsExpositions(writer, metrics);
        if (withEof) {
            writer.write("# EOF\n");
        }
        return writer.toString();
    }

    static void writeMetricFamilies(Writer writer, Map<String, List<Metric>> metricFamilies)
            throws IOException
    {
        for (List<Metric> metricFamily : metricFamilies.values()) {
            metricFamily.getFirst().writeMetricDescriptor(writer);
            for (Metric metric : metricFamily) {
                metric.writeMetricExposition(writer);
            }
        }
    }

    /**
     * Only include metric descriptor once per metric family per openmetrics spec:
     * <a href="https://prometheus.io/docs/specs/om/open_metrics_spec_2_0/#abnf">...</a>
     */
    static Map<String, List<Metric>> groupMetricFamilies(List<Metric> metrics)
    {
        // CompositeMetric should have at most one level of nesting, see CompositeMetric#from
        List<Metric> flattenedMetrics = metrics.stream()
                .flatMap(metric -> metric instanceof CompositeMetric compositeMetric ?
                        compositeMetric.subMetrics().stream() :
                        Stream.of(metric))
                .collect(toImmutableList());
        Map<String, List<Metric>> metricFamilies = flattenedMetrics.stream()
                .collect(groupingBy(
                        Metric::metricName,
                        LinkedHashMap::new,
                        toList()));

        metricFamilies.forEach((metricName, metricFamily) -> {
            Set<Class<?>> metricTypes = metricFamily.stream()
                    .map(Metric::getClass)
                    .collect(toImmutableSet());
            checkState(metricTypes.size() == 1, "Metric family %s contains mixed metric types: %s", metricName, metricTypes);
        });

        return metricFamilies;
    }

    public static void writeSingleValuedMetric(Writer writer, String name, Map<String, String> labels, String value)
            throws IOException
    {
        writer.write(name);
        String renderedLabels = renderLabels(labels);
        if (!renderedLabels.isEmpty()) {
            writer.write(renderedLabels);
        }
        writer.write(' ');
        writer.write(value);
        writer.write('\n');
    }

    public static void writeSingleMetricDescriptor(Writer writer, String metricName, String type, String help)
            throws IOException
    {
        writer.write("# TYPE ");
        writer.write(metricName);
        writer.write(' ');
        writer.write(type);
        writer.write('\n');
        if (!Strings.isNullOrEmpty(help)) {
            writer.write("# HELP ");
            writer.write(metricName);
            writer.write(' ');
            writer.write(escape(help));
            writer.write('\n');
        }
    }

    /**
     * Renders labels as {@code {key="value",...}} with keys sorted, or an empty string when there are no labels.
     */
    public static String renderLabels(Map<String, String> labels)
    {
        if (labels.isEmpty()) {
            return "";
        }
        return labels.entrySet().stream()
                .sorted(comparingByKey())
                .map(e -> e.getKey() + "=\"" + escape(e.getValue()) + "\"")
                .collect(joining(",", "{", "}"));
    }

    public static String escape(String value)
    {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> {} // drop
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }
}
