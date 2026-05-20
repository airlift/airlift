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
import com.google.inject.Inject;
import io.airlift.openmetrics.types.CompositeMetric;
import io.airlift.openmetrics.types.Metric;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Path("/metrics")
public class MetricsResource
{
    private static final String OPENMETRICS_CONTENT_TYPE = "application/openmetrics-text; version=1.0.0; charset=utf-8";

    private final OpenMetricsCollector collector;

    @Inject
    public MetricsResource(OpenMetricsCollector collector)
    {
        this.collector = requireNonNull(collector, "collector is null");
    }

    @GET
    @Produces(OPENMETRICS_CONTENT_TYPE)
    public String getMetrics(@QueryParam("name[]") List<String> filter)
    {
        List<Metric> metrics = collector.collect(filter);
        StringBuilder body = new StringBuilder();
        metricExpositions(body, metrics);
        body.append("# EOF\n");
        return body.toString();
    }

    @VisibleForTesting
    static String sanitizeMetricName(String name)
    {
        return OpenMetricsCollector.sanitizeMetricName(name);
    }

    /**
     * Only include metric descriptor once per metric family per openmetrics spec:
     * <a href="https://prometheus.io/docs/specs/om/open_metrics_spec_2_0/#abnf">...</a>
     */
    @VisibleForTesting
    static void metricExpositions(StringBuilder builder, List<Metric> metrics)
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

            builder.append(metricFamily.getFirst().getMetricDescriptor());
            for (Metric metric : metricFamily) {
                builder.append(metric.getMetricExposition());
            }
        });
    }
}
