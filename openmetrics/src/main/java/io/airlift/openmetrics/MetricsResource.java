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
import io.airlift.openmetrics.types.Metric;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import static io.airlift.openmetrics.MetricsUtils.groupMetricFamilies;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Path("/metrics")
public class MetricsResource
{
    private static final String OPENMETRICS_CONTENT_TYPE = "application/openmetrics-text; version=1.0.0; charset=utf-8";
    // OpenMetrics text is the default; protobuf is selected only after explicit negotiation.
    private static final String PROMETHEUS_PROTOBUF_CONTENT_TYPE = "application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited; qs=0.9";

    private final OpenMetricsCollector collector;

    @Inject
    public MetricsResource(OpenMetricsCollector collector)
    {
        this.collector = requireNonNull(collector, "collector is null");
    }

    @GET
    @Produces(OPENMETRICS_CONTENT_TYPE)
    public StreamingOutput getMetrics(@QueryParam("name[]") List<String> filter)
    {
        // collect, group, and validate eagerly so errors surface before the response is committed
        Map<String, List<Metric>> metricFamilies = groupMetricFamilies(collector.collect(filter));
        // metrics write directly to the response stream, so the exposition is never held in memory
        return output -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8));
            MetricsUtils.writeMetricFamilies(writer, metricFamilies);
            writer.write("# EOF\n");
            writer.flush();
        };
    }

    @GET
    @Produces(PROMETHEUS_PROTOBUF_CONTENT_TYPE)
    public StreamingOutput getPrometheusProtobufMetrics(@QueryParam("name[]") List<String> filter)
    {
        Map<String, List<Metric>> metricFamilies = groupMetricFamilies(collector.collectPrometheusProtobuf(filter));
        return output -> PrometheusProtobufWriter.writeMetricFamilies(output, metricFamilies);
    }

    @VisibleForTesting
    static String sanitizeMetricName(String name)
    {
        return OpenMetricsCollector.sanitizeMetricName(name);
    }
}
