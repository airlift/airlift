package io.airlift.opentelemetry;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.metrics.CollectedMetricGroup;
import io.airlift.metrics.MetricsCollector;
import io.airlift.opentelemetry.OpenTelemetryMetricDataConverter.ConversionResult;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class OpenTelemetryMetricProducer
        implements MetricProducer
{
    static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE =
            InstrumentationScopeInfo.builder("io.airlift.opentelemetry.metrics").build();

    private static final Logger log = Logger.get(OpenTelemetryMetricProducer.class);
    private static final String DROPPED_POINTS_METRIC_NAME = "opentelemetry_metrics_dropped_points";

    private final MetricsCollector collector;
    private final OpenTelemetryMetricDataConverter converter;
    private final AtomicLong droppedPoints = new AtomicLong();
    private final long epochNanoOffset;
    private final long startEpochNanos;

    @Inject
    public OpenTelemetryMetricProducer(MetricsCollector collector, OpenTelemetryMetricDataConverter converter)
    {
        this.collector = requireNonNull(collector, "collector is null");
        this.converter = requireNonNull(converter, "converter is null");
        epochNanoOffset = MILLISECONDS.toNanos(currentTimeMillis()) - nanoTime();
        startEpochNanos = epochNanoOffset + nanoTime();
    }

    @Override
    public Collection<MetricData> produce(Resource resource)
    {
        try {
            long epochNanos = epochNanoOffset + nanoTime();
            List<CollectedMetricGroup> metrics = collector.collect();
            ConversionResult conversionResult = converter.convertWithDroppedPoints(metrics, resource, startEpochNanos, epochNanos);
            long totalDroppedPoints = droppedPoints.addAndGet(conversionResult.droppedPoints());

            List<MetricData> metricData = new ArrayList<>(conversionResult.metricData());
            metricData.add(droppedPointsMetric(resource, startEpochNanos, epochNanos, totalDroppedPoints));
            return metricData;
        }
        catch (RuntimeException e) {
            log.warn(e, "Unable to produce OpenTelemetry metric data");
            return List.of();
        }
    }

    private static MetricData droppedPointsMetric(Resource resource, long startEpochNanos, long epochNanos, long totalDroppedPoints)
    {
        return ImmutableMetricData.createLongSum(
                resource,
                INSTRUMENTATION_SCOPE,
                DROPPED_POINTS_METRIC_NAME,
                "OpenTelemetry metric points dropped by the metric producer due to point limits.",
                "",
                SumData.createLongSumData(
                        true,
                        AggregationTemporality.CUMULATIVE,
                        List.of(LongPointData.create(startEpochNanos, epochNanos, Attributes.empty(), totalDroppedPoints))));
    }
}
