package io.airlift.opentelemetry;

import com.sun.net.httpserver.HttpServer;
import io.airlift.stats.ExponentialHistogram;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.SumData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.time.TestClock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.stats.ExponentialHistogram.DEFAULT_MAX_BUCKETS;
import static io.airlift.stats.ExponentialHistogram.MIN_SCALE;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.common.export.MemoryMode.REUSABLE_DATA;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA;
import static io.opentelemetry.sdk.metrics.data.MetricDataType.EXPONENTIAL_HISTOGRAM;
import static io.opentelemetry.sdk.metrics.data.MetricDataType.LONG_SUM;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class TestExponentialHistogramDeltaExporter
{
    @Test
    void testSuccessiveIntervals()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            histogram.record(-10);
            histogram.record(0);
            ExponentialHistogramPointData first = export(exporter, sink, metric(histogram, 20));
            assertThat(first.getStartEpochNanos()).isEqualTo(10);
            assertThat(first.getCount()).isEqualTo(3);
            assertThat(first.getMin()).isEqualTo(-10);
            assertThat(first.getMax()).isEqualTo(10);

            histogram.record(10, 2);
            histogram.record(-5);
            histogram.record(0);
            ExponentialHistogramPointData second = export(exporter, sink, metric(histogram, 30));
            assertThat(second.getStartEpochNanos()).isEqualTo(20);
            assertThat(second.getEpochNanos()).isEqualTo(30);
            assertThat(second.getCount()).isEqualTo(4);
            assertThat(second.getSum()).isEqualTo(15);
            assertThat(second.getZeroCount()).isEqualTo(1);
            assertThat(second.getPositiveBuckets().getTotalCount()).isEqualTo(2);
            assertThat(second.getNegativeBuckets().getTotalCount()).isEqualTo(1);
            assertThat(second.hasMin()).isFalse();
            assertThat(second.hasMax()).isFalse();

            ExponentialHistogramPointData empty = export(exporter, sink, metric(histogram, 40));
            assertThat(empty.getStartEpochNanos()).isEqualTo(30);
            assertThat(empty.getCount()).isZero();
            assertThat(empty.getSum()).isZero();
            assertThat(empty.getZeroCount()).isZero();
            assertThat(empty.getPositiveBuckets().getBucketCounts()).isEmpty();
            assertThat(empty.getNegativeBuckets().getBucketCounts()).isEmpty();
        }
    }

    @Test
    void testPreservesExemplarsAcrossIntervals()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            SpanContext spanContext = SpanContext.create(
                    "0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());
            DoubleExemplarData firstExemplar = DoubleExemplarData.create(Attributes.builder().put("request", "first").build(), 15, spanContext, 10);
            DoubleExemplarData secondExemplar = DoubleExemplarData.create(Attributes.builder().put("request", "second").build(), 18, spanContext, 20);
            List<DoubleExemplarData> exemplars = new ArrayList<>(List.of(firstExemplar, secondExemplar));
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            histogram.record(20);
            ExponentialHistogramPointData first = export(exporter, sink, withExemplars(metric(histogram, 20), exemplars));
            assertThat(first.getExemplars()).containsExactly(firstExemplar, secondExemplar);
            assertThat(first.getStartEpochNanos()).isEqualTo(10);
            assertThat(first.getEpochNanos()).isEqualTo(20);
            assertThat(first.getCount()).isEqualTo(2);
            assertThat(first.getSum()).isEqualTo(30);

            exemplars.clear();
            assertThat(first.getExemplars()).containsExactly(firstExemplar, secondExemplar);
            DoubleExemplarData thirdExemplar = DoubleExemplarData.create(Attributes.builder().put("request", "third").build(), 25, spanContext, 30);
            exemplars.add(firstExemplar);
            exemplars.add(thirdExemplar);
            histogram.record(30);
            ExponentialHistogramPointData delta = export(exporter, sink, withExemplars(metric(histogram, 30), exemplars));
            assertThat(delta.getExemplars()).containsExactly(firstExemplar, thirdExemplar);
            assertThat(delta.getStartEpochNanos()).isEqualTo(20);
            assertThat(delta.getEpochNanos()).isEqualTo(30);
            assertThat(delta.getCount()).isEqualTo(1);
            assertThat(delta.getSum()).isEqualTo(30);

            exemplars.clear();
            assertThat(delta.getExemplars()).containsExactly(firstExemplar, thirdExemplar);
            histogram.record(5);
            ExponentialHistogramPointData next = export(exporter, sink, withExemplars(metric(histogram, 40), exemplars));
            assertThat(next.getExemplars()).isEmpty();
            assertThat(next.getStartEpochNanos()).isEqualTo(30);
            assertThat(next.getEpochNanos()).isEqualTo(40);
            assertThat(next.getCount()).isEqualTo(1);
            assertThat(next.getSum()).isEqualTo(5);
        }
    }

    @Test
    void testPreservesExemplarsOnReset()
    {
        for (long startEpochNanos : List.of(10L, 30L)) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10, 4);
                export(exporter, sink, withExemplars(metric(histogram, 20), List.of()));

                histogram.reset();
                histogram.record(5);
                DoubleExemplarData exemplar = DoubleExemplarData.create(Attributes.empty(), 35, SpanContext.getInvalid(), 5);
                List<DoubleExemplarData> exemplars = new ArrayList<>(List.of(exemplar));
                ExponentialHistogramPointData reset = export(exporter, sink, withExemplars(metric(histogram, startEpochNanos, 40, Attributes.empty()), exemplars));
                assertThat(reset.getExemplars()).containsExactly(exemplar);
                assertThat(reset.getStartEpochNanos()).isEqualTo(startEpochNanos == 10 ? 20 : 30);
                assertThat(reset.getEpochNanos()).isEqualTo(40);
                assertThat(reset.getCount()).isEqualTo(1);
                assertThat(reset.getSum()).isEqualTo(5);
                assertThat(reset.hasMin()).isTrue();
                assertThat(reset.getMin()).isEqualTo(5);
                assertThat(reset.hasMax()).isTrue();
                assertThat(reset.getMax()).isEqualTo(5);

                exemplars.clear();
                assertThat(reset.getExemplars()).containsExactly(exemplar);
                histogram.record(5);
                ExponentialHistogramPointData next = export(exporter, sink, withExemplars(metric(histogram, startEpochNanos, 50, Attributes.empty()), exemplars));
                assertThat(next.getExemplars()).isEmpty();
                assertThat(next.getStartEpochNanos()).isEqualTo(40);
                assertThat(next.getEpochNanos()).isEqualTo(50);
                assertThat(next.getCount()).isEqualTo(1);
                assertThat(next.getSum()).isEqualTo(5);
            }
        }
    }

    @Test
    void testDownscaledBuckets()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram(20, 4);
            histogram.record(0.5);
            histogram.record(-0.5);
            ExponentialHistogramPointData first = export(exporter, sink, metric(histogram, 20));

            histogram.record(1024);
            histogram.record(-1024, 2);
            ExponentialHistogramPointData delta = export(exporter, sink, metric(histogram, 30));
            assertThat(delta.getScale()).isLessThan(first.getScale());
            assertThat(delta.getCount()).isEqualTo(3);
            assertThat(delta.getSum()).isEqualTo(-1024);

            ExponentialHistogram expected = new ExponentialHistogram(delta.getScale(), 4);
            expected.record(1024);
            expected.record(-1024, 2);
            ExponentialHistogramPointData expectedPoint = point(metric(expected, 30));
            assertThat(delta.getPositiveBuckets()).isEqualTo(expectedPoint.getPositiveBuckets());
            assertThat(delta.getNegativeBuckets()).isEqualTo(expectedPoint.getNegativeBuckets());
        }
    }

    @Test
    void testReset()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10, 4);
            export(exporter, sink, metric(histogram, 20));

            histogram.reset();
            histogram.record(5);
            ExponentialHistogramPointData reset = export(exporter, sink, metric(histogram, 30));
            assertThat(reset.getStartEpochNanos()).isEqualTo(20);
            assertThat(reset.getCount()).isEqualTo(1);
            assertThat(reset.getSum()).isEqualTo(5);
            assertThat(reset.hasMin()).isTrue();
            assertThat(reset.getMin()).isEqualTo(5);

            histogram.record(5);
            assertThat(export(exporter, sink, metric(histogram, 40)).getCount()).isEqualTo(1);
        }
    }

    @Test
    void testResetWithIncreasedCount()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));

            histogram.reset();
            histogram.record(100, 2);
            ExponentialHistogramPointData reset = export(exporter, sink, metric(histogram, 30));
            assertThat(reset.getStartEpochNanos()).isEqualTo(20);
            assertThat(reset.getCount()).isEqualTo(2);
            assertThat(reset.getSum()).isEqualTo(200);
            assertThat(reset.getPositiveBuckets().getTotalCount()).isEqualTo(2);
        }
    }

    @Test
    void testNewStartTime()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));

            ExponentialHistogramPointData point = point(metric(histogram, 40));
            ExponentialHistogramPointData restarted = ExponentialHistogramPointData.create(
                    point.getScale(),
                    point.getSum(),
                    point.getZeroCount(),
                    point.hasMin(),
                    point.getMin(),
                    point.hasMax(),
                    point.getMax(),
                    point.getPositiveBuckets(),
                    point.getNegativeBuckets(),
                    30,
                    40,
                    point.getAttributes(),
                    List.of());
            MetricData metric = ImmutableMetricData.createExponentialHistogram(
                    Resource.empty(), OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE, "test", "", "ns", ExponentialHistogramData.create(CUMULATIVE, List.of(restarted)));
            assertThat(export(exporter, sink, metric)).isEqualTo(restarted);
        }
    }

    @Test
    void testIndependentReaders()
            throws Exception
    {
        InMemoryMetricExporter firstSink = InMemoryMetricExporter.create(DELTA);
        InMemoryMetricExporter secondSink = InMemoryMetricExporter.create(DELTA);
        ExponentialHistogram histogram = new ExponentialHistogram();
        AtomicLong epochNanos = new AtomicLong(10);
        try (PeriodicMetricReader firstReader = PeriodicMetricReader.builder(new ExponentialHistogramDeltaExporter(firstSink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))).setInterval(Duration.ofDays(1)).build();
                PeriodicMetricReader secondReader = PeriodicMetricReader.builder(new ExponentialHistogramDeltaExporter(secondSink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))).setInterval(Duration.ofDays(1)).build();
                SdkMeterProvider provider = SdkMeterProvider.builder()
                        .registerMetricReader(firstReader)
                        .registerMetricReader(secondReader)
                        .registerMetricProducer(_ -> List.of(metric(histogram, epochNanos.addAndGet(10))))
                        .build()) {
            histogram.record(10);
            assertThat(firstReader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            histogram.record(20);
            assertThat(secondReader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            assertThat(firstReader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();

            assertThat(firstSink.getFinishedMetricItems().stream()
                    .filter(metric -> metric.getName().equals("test"))
                    .map(TestExponentialHistogramDeltaExporter::point)
                    .map(ExponentialHistogramPointData::getSum))
                    .containsExactly(10.0, 20.0);
            assertThat(secondSink.getFinishedMetricItems().stream()
                    .filter(metric -> metric.getName().equals("test"))
                    .map(TestExponentialHistogramDeltaExporter::point)
                    .map(ExponentialHistogramPointData::getSum))
                    .containsExactly(30.0);

            firstSink.reset();
            secondSink.reset();
            histogram.record(5);
            assertThat(provider.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            assertThat(point(firstSink.getFinishedMetricItems().stream().filter(metric -> metric.getName().equals("test")).collect(onlyElement())).getSum()).isEqualTo(5);
            assertThat(point(secondSink.getFinishedMetricItems().stream().filter(metric -> metric.getName().equals("test")).collect(onlyElement())).getSum()).isEqualTo(5);
        }
    }

    @Test
    void testSeparateMetricIdentities()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(5);
            MetricData metric = metric(histogram, 20);
            ExponentialHistogram otherHistogram = new ExponentialHistogram();
            otherHistogram.record(5, 3);
            Resource otherResource = Resource.create(Attributes.builder().put("service.name", "other").build());
            List<MetricData> metrics = List.of(
                    metric,
                    ImmutableMetricData.createExponentialHistogram(
                            otherResource, metric.getInstrumentationScopeInfo(), metric.getName(), "", "ns", metric.getExponentialHistogramData()),
                    ImmutableMetricData.createExponentialHistogram(
                            metric.getResource(), InstrumentationScopeInfo.create("other"), metric.getName(), "", "ns", metric.getExponentialHistogramData()),
                    ImmutableMetricData.createExponentialHistogram(
                            metric.getResource(), metric.getInstrumentationScopeInfo(), "other", "", "ns", metric.getExponentialHistogramData()),
                    ImmutableMetricData.createExponentialHistogram(
                            metric.getResource(), metric.getInstrumentationScopeInfo(), metric.getName(), "", "ms", metric.getExponentialHistogramData()),
                    metric(otherHistogram, 20, Attributes.builder().put("rule", "other").build()));
            assertThat(exporter.export(metrics).isSuccess()).isTrue();
            assertThat(exporter.export(metrics).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems().stream().map(TestExponentialHistogramDeltaExporter::point).map(ExponentialHistogramPointData::getCount))
                    .containsExactly(1L, 1L, 1L, 1L, 1L, 3L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    @Test
    void testMissingCollection()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));
            sink.reset();
            assertThat(exporter.export(List.of()).isSuccess()).isTrue();

            histogram.record(20);
            assertThat(exporter.export(List.of(metric(histogram, 30))).isSuccess()).isTrue();
            assertThat(point(sink.getFinishedMetricItems().getFirst()).getSum()).isEqualTo(20);

            histogram.record(5);
            ExponentialHistogramPointData delta = export(exporter, sink, metric(histogram, 40));
            assertThat(delta.getStartEpochNanos()).isEqualTo(30);
            assertThat(delta.getCount()).isEqualTo(1);
            assertThat(delta.getSum()).isEqualTo(5);
        }
    }

    @Test
    void testCumulativeExporter()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(CUMULATIVE);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            MetricData metric = metric(new ExponentialHistogram(), 20);
            assertThat(exporter.export(List.of(metric)).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).containsExactly(metric);
        }
    }

    @Test
    void testDeltaHistogram()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            MetricData cumulative = metric(histogram, 20);
            MetricData delta = ImmutableMetricData.createExponentialHistogram(
                    cumulative.getResource(),
                    cumulative.getInstrumentationScopeInfo(),
                    cumulative.getName(),
                    "",
                    "ns",
                    ExponentialHistogramData.create(DELTA, cumulative.getExponentialHistogramData().getPoints()));
            assertThat(export(exporter, sink, delta).getCount()).isEqualTo(1);
            assertThat(export(exporter, sink, delta).getCount()).isEqualTo(1);
        }
    }

    @Test
    void testSdkMetrics()
            throws Exception
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (PeriodicMetricReader reader = PeriodicMetricReader.builder(new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))).setInterval(Duration.ofDays(1)).build();
                SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build()) {
            var meter = provider.get("test");
            var counter = meter.counterBuilder("counter").build();
            var histogram = meter.histogramBuilder("histogram").build();
            counter.add(2);
            histogram.record(5);
            assertThat(reader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            counter.add(3);
            histogram.record(10);
            assertThat(reader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();

            assertThat(sink.getFinishedMetricItems().stream().filter(metric -> metric.getName().equals("counter"))
                    .map(metric -> metric.getLongSumData().getPoints().iterator().next().getValue())).containsExactly(2L, 3L);
            assertThat(sink.getFinishedMetricItems().stream().filter(metric -> metric.getName().equals("histogram"))
                    .map(metric -> metric.getHistogramData().getPoints().iterator().next().getSum())).containsExactly(5.0, 10.0);
        }
    }

    @Test
    void testInitialValuePolicy()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        TestClock clock = TestClock.create(Instant.ofEpochSecond(0, 15));
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            assertThat(exporter.export(List.of(metric(histogram, 20))).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).isEmpty();
            histogram.record(20);
            assertThat(export(exporter, sink, metric(histogram, 30)).getSum()).isEqualTo(20);
        }
    }

    @Test
    void testZeroDurationAndMissingStart()
    {
        for (long start : List.of(0L, 20L)) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.ofEpochSecond(0, 5)))) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10);
                assertThat(exporter.export(List.of(metric(histogram, start, 20, Attributes.empty()))).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).isEmpty();
                histogram.record(5);
                assertThat(export(exporter, sink, metric(histogram, start, 30, Attributes.empty())).getSum()).isEqualTo(5);
            }
        }
    }

    @Test
    void testZeroDurationResetEstablishesBaseline()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, withExemplars(metric(histogram, 20), List.of()));
            sink.reset();

            histogram.reset();
            histogram.record(20);
            DoubleExemplarData exemplar = DoubleExemplarData.create(Attributes.empty(), 30, SpanContext.getInvalid(), 20);
            assertThat(exporter.export(List.of(withExemplars(metric(histogram, 30, 30, Attributes.empty()), List.of(exemplar)))).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).isEmpty();

            histogram.record(5, 2);
            ExponentialHistogramPointData delta = export(exporter, sink, withExemplars(metric(histogram, 30, 40, Attributes.empty()), List.of()));
            assertThat(delta.getStartEpochNanos()).isEqualTo(30);
            assertThat(delta.getEpochNanos()).isEqualTo(40);
            assertThat(delta.getCount()).isEqualTo(2);
            assertThat(delta.getSum()).isEqualTo(10);
            assertThat(delta.getExemplars()).isEmpty();
        }
    }

    @Test
    void testLateSeriesUsesExporterStart()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));
            assertThat(export(exporter, sink, metric(histogram, 30, Attributes.builder().put("series", "late").build())).getSum()).isEqualTo(10);
        }
    }

    @Test
    void testHistoryLimitsAndExpiry()
    {
        for (boolean limitFamily : List.of(false, true)) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            TestClock clock = TestClock.create(Instant.EPOCH);
            OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                    .setHistogramMaxTrackedSeries(limitFamily ? 100 : 2)
                    .setHistogramMaxTrackedSeriesPerMetric(limitFamily ? 2 : 100)
                    .setHistogramMaxStaleness(new io.airlift.units.Duration(1, SECONDS));
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10);
                Attributes first = Attributes.builder().put("series", "first").build();
                Attributes second = Attributes.builder().put("series", "second").build();
                export(exporter, sink, metric(histogram, 20, first));
                export(exporter, sink, metric(histogram, 20, second));
                sink.reset();
                for (int i = 0; i < 100; i++) {
                    assertThat(exporter.export(List.of(metric(histogram, 30, Attributes.builder().put("series", "overflow-" + i).build()))).isSuccess()).isTrue();
                }
                assertThat(sink.getFinishedMetricItems()).hasSize(100).allSatisfy(metric -> assertThat(metric.getName()).isEqualTo("opentelemetry_metrics_exporter_dropped_points"));
                assertThat(droppedPoints(sink).getValue()).isEqualTo(100);
                histogram.record(5);
                clock.advance(Duration.ofMillis(500));
                assertThat(export(exporter, sink, metric(histogram, 40, first)).getSum()).isEqualTo(5);
                clock.advance(Duration.ofMillis(500));
                assertThat(export(exporter, sink, metric(histogram, 50, Attributes.empty())).getSum()).isEqualTo(15);
                assertThat(export(exporter, sink, metric(histogram, 60, first)).getSum()).isZero();
            }
        }
    }

    @Test
    void testCollectionsBelowStalenessRetainHistory()
    {
        for (long sourceStart : new long[] {0, 20}) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            TestClock clock = TestClock.create(Instant.ofEpochSecond(0, 10));
            OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                    .setInterval(new io.airlift.units.Duration(1, SECONDS))
                    .setHistogramMaxStaleness(new io.airlift.units.Duration(2, SECONDS));
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10);
                assertThat(exporter.export(List.of(metric(histogram, sourceStart, 30, Attributes.empty()))).isSuccess()).isTrue();
                if (sourceStart == 0) {
                    assertThat(sink.getFinishedMetricItems()).isEmpty();
                }
                for (int collection = 1; collection <= 3; collection++) {
                    clock.advance(Duration.ofSeconds(1));
                    histogram.record(10);
                    ExponentialHistogramPointData delta = export(exporter, sink, metric(histogram, sourceStart, 30 + SECONDS.toNanos(collection), Attributes.empty()));
                    assertThat(delta.getCount()).isEqualTo(1);
                    assertThat(delta.getSum()).isEqualTo(10);
                    assertThat(delta.getStartEpochNanos()).isEqualTo(30 + SECONDS.toNanos(collection - 1));
                }
            }
        }
    }

    @Test
    void testReplacementSeriesWaitForExpiry()
    {
        for (boolean limitFamily : List.of(false, true)) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            TestClock clock = TestClock.create(Instant.EPOCH);
            OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                    .setHistogramMaxTrackedSeries(limitFamily ? 100 : 2)
                    .setHistogramMaxTrackedSeriesPerMetric(limitFamily ? 2 : 100)
                    .setInterval(new io.airlift.units.Duration(1, SECONDS))
                    .setHistogramMaxStaleness(new io.airlift.units.Duration(2, SECONDS));
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10);
                List<MetricData> original = List.of(
                        metric(histogram, 20, Attributes.builder().put("series", "first").build()),
                        metric(histogram, 20, Attributes.builder().put("series", "second").build()));
                List<MetricData> replacements = List.of(
                        metric(histogram, 30, Attributes.builder().put("series", "third").build()),
                        metric(histogram, 30, Attributes.builder().put("series", "fourth").build()));
                assertThat(exporter.export(original).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(2);
                sink.reset();
                clock.advance(Duration.ofSeconds(1));
                assertThat(exporter.export(replacements).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(1);
                assertThat(droppedPoints(sink).getValue()).isEqualTo(2);
                sink.reset();
                clock.advance(Duration.ofSeconds(1));
                assertThat(exporter.export(replacements).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(3);
                assertThat(sink.getFinishedMetricItems().stream().filter(data -> data.getType() == EXPONENTIAL_HISTOGRAM))
                        .map(TestExponentialHistogramDeltaExporter::point)
                        .containsExactly(point(replacements.getFirst()), point(replacements.getLast()));
                assertThat(droppedPoints(sink).getValue()).isEqualTo(2);
            }
        }
    }

    @Test
    void testCapacityDropTotalsAndPassThrough()
    {
        for (OpenTelemetryExporterConfig config : List.of(
                new OpenTelemetryExporterConfig().setHistogramMaxTrackedSeries(1),
                new OpenTelemetryExporterConfig().setHistogramMaxTrackedSeriesPerMetric(1),
                new OpenTelemetryExporterConfig().setHistogramMaxTrackedSeries(1).setHistogramMaxTrackedSeriesPerMetric(1))) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
            TestClock clock = TestClock.create(Instant.ofEpochSecond(0, 15));
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
                ExponentialHistogram histogram = new ExponentialHistogram();
                histogram.record(10);
                MetricData admitted = metric(histogram, 20);
                MetricData rejected = metric(histogram, 20, Attributes.builder().put("series", "rejected").build());
                assertThat(exporter.export(List.of(admitted)).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).isEmpty();
                assertThat(exporter.export(List.of(rejected, rejected)).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(1);
                LongPointData first = droppedPoints(sink);
                assertThat(first.getValue()).isEqualTo(2);
                assertThat(first.getStartEpochNanos()).isEqualTo(15);
                assertThat(first.getEpochNanos()).isEqualTo(15);
                sink.reset();
                clock.advance(Duration.ofMinutes(1));
                assertThat(exporter.export(List.of(rejected)).isSuccess()).isTrue();
                assertThat(droppedPoints(sink).getValue()).isEqualTo(3);
                sink.reset();
                clock.advance(Duration.ofSeconds(1));
                assertThat(exporter.export(List.of()).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(1);
                LongPointData later = droppedPoints(sink);
                assertThat(later.getValue()).isEqualTo(3);
                assertThat(later.getStartEpochNanos()).isEqualTo(first.getStartEpochNanos());
                assertThat(later.getEpochNanos()).isEqualTo(15 + SECONDS.toNanos(61));
                assertThat(later.getAttributes()).isEqualTo(first.getAttributes());
                MetricData producerDrops = ImmutableMetricData.createLongSum(
                        Resource.empty(),
                        OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE,
                        "opentelemetry_metrics_dropped_points",
                        "",
                        "",
                        SumData.createLongSumData(true, CUMULATIVE, List.of(LongPointData.create(0, 20, Attributes.empty(), 7))));
                MetricData delta = ImmutableMetricData.createExponentialHistogram(
                        admitted.getResource(),
                        admitted.getInstrumentationScopeInfo(),
                        "delta",
                        "",
                        "ns",
                        ExponentialHistogramData.create(DELTA, admitted.getExponentialHistogramData().getPoints()));
                sink.reset();
                assertThat(exporter.export(List.of(producerDrops, delta)).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).hasSize(3);
                assertThat(sink.getFinishedMetricItems().getFirst()).isSameAs(producerDrops);
                assertThat(sink.getFinishedMetricItems().get(1)).isSameAs(delta);
                assertThat(droppedPoints(sink).getValue()).isEqualTo(3);
            }
        }
    }

    @Test
    void testDropMetricResourceAndExporterIdentity()
    {
        Resource resource = Resource.create(Attributes.builder().put("service.name", "application").build());
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig().setHistogramMaxTrackedSeries(1);
        InMemoryMetricExporter firstSink = InMemoryMetricExporter.create(DELTA);
        InMemoryMetricExporter secondSink = InMemoryMetricExporter.create(DELTA);
        TestClock clock = TestClock.create(Instant.EPOCH);
        try (ExponentialHistogramDeltaExporter first = new ExponentialHistogramDeltaExporter(firstSink, config, resource, clock);
                ExponentialHistogramDeltaExporter second = new ExponentialHistogramDeltaExporter(secondSink, config, resource, clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            List<MetricData> metrics = List.of(
                    metric(histogram, 20),
                    metric(histogram, 20, Attributes.builder().put("series", "rejected").build()));
            assertThat(first.export(metrics).isSuccess()).isTrue();
            assertThat(first.export(metrics).isSuccess()).isTrue();
            assertThat(second.export(metrics).isSuccess()).isTrue();
            assertThat(droppedPoints(firstSink).getValue()).isEqualTo(2);
            assertThat(droppedPoints(secondSink).getValue()).isEqualTo(1);
            for (InMemoryMetricExporter sink : List.of(firstSink, secondSink)) {
                MetricData diagnostic = sink.getFinishedMetricItems().getLast();
                assertThat(diagnostic.getResource()).isSameAs(resource);
                assertThat(diagnostic.getInstrumentationScopeInfo()).isEqualTo(OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE);
                assertThat(diagnostic.getType()).isEqualTo(LONG_SUM);
                assertThat(diagnostic.getLongSumData().isMonotonic()).isTrue();
                assertThat(diagnostic.getLongSumData().getAggregationTemporality()).isEqualTo(CUMULATIVE);
                Attributes attributes = droppedPoints(sink).getAttributes();
                assertThat(attributes.size()).isEqualTo(2);
                assertThat(attributes.get(stringKey("otel.component.type"))).isEqualTo("airlift_histogram_delta_exporter");
                assertThat(attributes.get(stringKey("otel.component.name"))).startsWith("airlift_histogram_delta_exporter/");
            }
            assertThat(droppedPoints(firstSink).getAttributes().get(stringKey("otel.component.name")))
                    .isNotEqualTo(droppedPoints(secondSink).getAttributes().get(stringKey("otel.component.name")));
        }
    }

    @Test
    void testDropMetricUsesMonotonicTime()
    {
        TestClock wallClock = TestClock.create(Instant.ofEpochSecond(10));
        TestClock elapsedClock = TestClock.create(Instant.ofEpochSecond(1));
        Clock clock = new Clock()
        {
            @Override
            public long now()
            {
                return wallClock.now();
            }

            @Override
            public long nanoTime()
            {
                return elapsedClock.nanoTime();
            }
        };
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig().setHistogramMaxTrackedSeries(1), clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            assertThat(exporter.export(List.of(metric(histogram, 20), metric(histogram, 20, Attributes.builder().put("series", "rejected").build()))).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getEpochNanos()).isEqualTo(SECONDS.toNanos(10));
            wallClock.setTime(Instant.EPOCH);
            elapsedClock.advance(Duration.ofSeconds(1));
            assertThat(exporter.export(List.of()).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getStartEpochNanos()).isEqualTo(SECONDS.toNanos(10));
            assertThat(droppedPoints(sink).getEpochNanos()).isEqualTo(SECONDS.toNanos(11));
        }
    }

    @Test
    void testFailedExportDoesNotResetDropTotal()
            throws Exception
    {
        AtomicLong requests = new AtomicLong();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/metrics", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(requests.incrementAndGet() == 1 ? 400 : 200, -1);
            }
        });
        server.start();
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF)
                .setEndpoint(URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/metrics"))
                .setHistogramMaxTrackedSeries(1);
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        MetricExporter delegate = OpenTelemetryExporterModule.createMetricExporter(config);
        MetricExporter recordingExporter = new MetricExporter()
        {
            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType)
            {
                return delegate.getAggregationTemporality(instrumentType);
            }

            @Override
            public CompletableResultCode export(Collection<MetricData> metrics)
            {
                sink.export(metrics);
                return delegate.export(metrics);
            }

            @Override
            public CompletableResultCode flush()
            {
                return delegate.flush();
            }

            @Override
            public CompletableResultCode shutdown()
            {
                return delegate.shutdown();
            }
        };
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(recordingExporter, config, TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            MetricData rejected = metric(histogram, 20, Attributes.builder().put("series", "rejected").build());
            assertThat(exporter.export(List.of(metric(histogram, 20), rejected)).join(10, SECONDS).isSuccess()).isFalse();
            assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
            assertThat(exporter.export(List.of(rejected)).join(10, SECONDS).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getValue()).isEqualTo(2);
            assertThat(exporter.export(List.of()).join(10, SECONDS).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getValue()).isEqualTo(2);
            assertThat(requests.get()).isEqualTo(3);
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void testExpiredSeriesFollowsInitialValuePolicy()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        TestClock clock = TestClock.create(Instant.EPOCH);
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig().setHistogramMaxStaleness(new io.airlift.units.Duration(1, SECONDS));
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));
            clock.advance(Duration.ofSeconds(1));
            histogram.record(5);
            assertThat(export(exporter, sink, metric(histogram, 30)).getSum()).isEqualTo(15);
        }
    }

    @Test
    void testOversizedBucketsAreDownscaled()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram(10, 1_024);
            for (int i = 0; i < 1_000; i++) {
                histogram.record(1 + i / 1_000.0);
                histogram.record(-1 - i / 1_000.0);
            }
            assertThat(histogram.snapshot().positiveBuckets().counts().length).isGreaterThan(160);
            ExponentialHistogramPointData first = export(exporter, sink, metric(histogram, 20));
            assertThat(first.getPositiveBuckets().getBucketCounts()).hasSizeLessThanOrEqualTo(160);
            assertThat(first.getNegativeBuckets().getBucketCounts()).hasSizeLessThanOrEqualTo(160);
            assertThat(first.getCount()).isEqualTo(2_000);
            histogram.record(1.5);
            assertThat(export(exporter, sink, metric(histogram, 30)).getSum()).isEqualTo(1.5);
        }
    }

    @Test
    void testUnshrinkableBucketsAreDropped()
    {
        for (int scale : List.of(MIN_SCALE, MIN_SCALE + 1)) {
            int bucketCount = (DEFAULT_MAX_BUCKETS + 1) << (scale - MIN_SCALE);
            for (int positiveSize : List.of(0, bucketCount)) {
                for (int negativeSize : List.of(0, bucketCount)) {
                    if (positiveSize == 0 && negativeSize == 0) {
                        continue;
                    }
                    InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
                    OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                            .setHistogramMaxTrackedSeries(1)
                            .setHistogramMaxTrackedSeriesPerMetric(1);
                    try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, TestClock.create(Instant.EPOCH))) {
                        ExponentialHistogram histogram = new ExponentialHistogram();
                        histogram.record(10);
                        MetricData valid = metric(histogram, 20);
                        MetricData rejected = withBucketCounts(
                                metric(histogram, 20, Attributes.builder().put("series", "rejected").build()),
                                scale,
                                nCopies(positiveSize, 1L),
                                nCopies(negativeSize, 1L));
                        MetricData mixed = ImmutableMetricData.createExponentialHistogram(
                                valid.getResource(),
                                valid.getInstrumentationScopeInfo(),
                                valid.getName(),
                                valid.getDescription(),
                                valid.getUnit(),
                                ExponentialHistogramData.create(CUMULATIVE, List.of(point(rejected), point(valid))));
                        assertThat(exporter.export(List.of(mixed)).isSuccess()).isTrue();
                        assertThat(sink.getFinishedMetricItems()).hasSize(2);
                        assertThat(point(sink.getFinishedMetricItems().getFirst())).isEqualTo(point(valid));
                        assertThat(sink.getFinishedMetricItems().getFirst().getExponentialHistogramData().getAggregationTemporality()).isEqualTo(DELTA);
                        assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
                    }
                }
            }
        }
    }

    @Test
    void testUnshrinkableUpdatePreservesBaseline()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10, 2);
            export(exporter, sink, metric(histogram, 20));
            sink.reset();
            MetricData rejected = withBucketCounts(metric(histogram, 30), MIN_SCALE, nCopies(DEFAULT_MAX_BUCKETS + 1, 1L), List.of());
            assertThat(exporter.export(List.of(rejected)).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).hasSize(1);
            assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
            histogram.record(10, 3);
            ExponentialHistogramPointData delta = export(exporter, sink, metric(histogram, 40));
            assertThat(delta.getStartEpochNanos()).isEqualTo(20);
            assertThat(delta.getEpochNanos()).isEqualTo(40);
            assertThat(delta.getCount()).isEqualTo(3);
            assertThat(delta.getSum()).isEqualTo(30);
            assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
        }
    }

    @Test
    void testUnshrinkableUpdateDoesNotDelayExpiry()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        TestClock clock = TestClock.create(Instant.EPOCH);
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setHistogramMaxTrackedSeries(2)
                .setHistogramMaxTrackedSeriesPerMetric(2)
                .setInterval(new io.airlift.units.Duration(1, SECONDS))
                .setHistogramMaxStaleness(new io.airlift.units.Duration(2, SECONDS));
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));
            clock.advance(Duration.ofSeconds(1));
            export(exporter, sink, metric(histogram, 30, Attributes.builder().put("series", "second").build()));
            MetricData rejected = withBucketCounts(metric(histogram, 30), MIN_SCALE, nCopies(DEFAULT_MAX_BUCKETS + 1, 1L), List.of());
            assertThat(exporter.export(List.of(rejected)).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
            clock.advance(Duration.ofSeconds(1));
            MetricData replacement = metric(histogram, 40, Attributes.builder().put("series", "replacement").build());
            assertThat(export(exporter, sink, replacement)).isEqualTo(point(replacement));
            assertThat(droppedPoints(sink).getValue()).isEqualTo(1);
        }
    }

    @Test
    void testBucketAndCapacityDropsShareTotal()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        TestClock clock = TestClock.create(Instant.EPOCH);
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setHistogramMaxTrackedSeries(1)
                .setHistogramMaxTrackedSeriesPerMetric(1);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, config, clock)) {
            ExponentialHistogram histogram = new ExponentialHistogram();
            histogram.record(10);
            export(exporter, sink, metric(histogram, 20));
            sink.reset();
            MetricData bucketRejected = withBucketCounts(metric(histogram, 30), MIN_SCALE, nCopies(DEFAULT_MAX_BUCKETS + 1, 1L), List.of());
            MetricData capacityRejected = metric(histogram, 30, Attributes.builder().put("series", "rejected").build());
            MetricData bothRejected = withBucketCounts(capacityRejected, MIN_SCALE, nCopies(DEFAULT_MAX_BUCKETS + 1, 1L), List.of());
            assertThat(exporter.export(List.of(bucketRejected, capacityRejected, bothRejected)).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).hasSize(1);
            assertThat(droppedPoints(sink).getValue()).isEqualTo(3);
            sink.reset();
            clock.advance(Duration.ofMinutes(1));
            assertThat(exporter.export(List.of(bucketRejected)).isSuccess()).isTrue();
            assertThat(droppedPoints(sink).getValue()).isEqualTo(4);
            sink.reset();
            assertThat(exporter.export(List.of()).isSuccess()).isTrue();
            assertThat(sink.getFinishedMetricItems()).hasSize(1);
            assertThat(droppedPoints(sink).getValue()).isEqualTo(4);
        }
    }

    @Test
    void testUnshrinkableBucketsPassThroughWithoutConversion()
    {
        for (AggregationTemporality temporality : List.of(CUMULATIVE, DELTA)) {
            InMemoryMetricExporter sink = InMemoryMetricExporter.create(temporality);
            try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
                MetricData oversized = withBucketCounts(metric(new ExponentialHistogram(), 20), MIN_SCALE, nCopies(DEFAULT_MAX_BUCKETS + 1, 1L), List.of());
                MetricData metric = ImmutableMetricData.createExponentialHistogram(
                        oversized.getResource(),
                        oversized.getInstrumentationScopeInfo(),
                        oversized.getName(),
                        oversized.getDescription(),
                        oversized.getUnit(),
                        ExponentialHistogramData.create(temporality, oversized.getExponentialHistogramData().getPoints()));
                assertThat(exporter.export(List.of(metric)).isSuccess()).isTrue();
                assertThat(sink.getFinishedMetricItems()).containsExactly(metric);
            }
        }
    }

    @Test
    void testCopiesMutableBucketData()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        try (ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH))) {
            List<Long> counts = new ArrayList<>(List.of(1L));
            ExponentialHistogramPointData first = ExponentialHistogramPointData.create(
                    0,
                    1,
                    0,
                    false,
                    0,
                    false,
                    0,
                    ExponentialHistogramBuckets.create(0, -1, counts),
                    ExponentialHistogramBuckets.create(0, 0, List.of()),
                    10,
                    20,
                    Attributes.empty(),
                    List.of());
            MetricData metric = ImmutableMetricData.createExponentialHistogram(
                    Resource.empty(),
                    OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE,
                    "test",
                    "",
                    "ns",
                    ExponentialHistogramData.create(CUMULATIVE, List.of(first)));
            export(exporter, sink, metric);
            counts.set(0, 99L);
            ExponentialHistogram histogram = new ExponentialHistogram(0, 160);
            histogram.record(1, 2);
            ExponentialHistogramPointData delta = export(exporter, sink, metric(histogram, 30));
            assertThat(delta.getCount()).isEqualTo(1);
            assertThat(delta.getSum()).isEqualTo(1);
        }
    }

    @Test
    void testDirectExporterMemoryMode()
            throws Exception
    {
        for (OpenTelemetryExporterConfig.Protocol protocol : OpenTelemetryExporterConfig.Protocol.values()) {
            OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig().setProtocol(protocol);
            try (var direct = OpenTelemetryExporterModule.createMetricExporter(config);
                    var reader = OpenTelemetryExporterModule.createMetricReader(config)) {
                assertThat(reader.getMemoryMode()).isEqualTo(direct.getMemoryMode());
                assertThat(reader.getMemoryMode()).isEqualTo(REUSABLE_DATA);
            }
        }
    }

    @Test
    void testShutdownRejectsFurtherHistory()
    {
        InMemoryMetricExporter sink = InMemoryMetricExporter.create(DELTA);
        ExponentialHistogramDeltaExporter exporter = new ExponentialHistogramDeltaExporter(sink, new OpenTelemetryExporterConfig(), TestClock.create(Instant.EPOCH));
        assertThat(exporter.shutdown().isSuccess()).isTrue();
        assertThat(exporter.export(List.of(metric(new ExponentialHistogram(), 20))).isSuccess()).isFalse();
    }

    @Test
    void testSlowAndFailedExportDoesNotQueueCollections()
            throws Exception
    {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong requests = new AtomicLong();
        AtomicLong collections = new AtomicLong();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/metrics", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                long request = requests.incrementAndGet();
                received.countDown();
                try {
                    if (!release.await(10, SECONDS)) {
                        throw new IOException("Timed out waiting to release the export");
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                exchange.sendResponseHeaders(request == 1 ? 400 : 200, -1);
            }
        });
        server.start();
        OpenTelemetryExporterConfig config = new OpenTelemetryExporterConfig()
                .setProtocol(OpenTelemetryExporterConfig.Protocol.HTTP_PROTOBUF)
                .setEndpoint(URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/metrics"));
        try (PeriodicMetricReader reader = PeriodicMetricReader.builder(new ExponentialHistogramDeltaExporter(
                        OpenTelemetryExporterModule.createMetricExporter(config), config, TestClock.create(Instant.EPOCH)))
                .setInterval(Duration.ofDays(1)).build();
                SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader)
                        .registerMetricProducer(_ -> {
                            long count = collections.incrementAndGet();
                            ExponentialHistogram histogram = new ExponentialHistogram();
                            histogram.record(10, count);
                            return List.of(metric(histogram, 20 + count));
                        })
                        .build()) {
            var counter = provider.get("test").counterBuilder("counter").build();
            counter.add(1);
            var firstExport = reader.forceFlush();
            assertThat(received.await(10, SECONDS)).isTrue();
            for (int i = 0; i < 10; i++) {
                reader.forceFlush();
            }
            assertThat(collections.get()).isEqualTo(1);
            assertThat(requests.get()).isEqualTo(1);
            release.countDown();
            // The reader reports flush completion even when its exporter returns a failure.
            assertThat(firstExport.join(10, SECONDS).isSuccess()).isTrue();
            counter.add(2);
            assertThat(reader.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            assertThat(collections.get()).isEqualTo(2);
            assertThat(requests.get()).isEqualTo(2);
        }
        finally {
            release.countDown();
            server.stop(0);
        }
    }

    private static LongPointData droppedPoints(InMemoryMetricExporter sink)
    {
        MetricData metric = sink.getFinishedMetricItems().getLast();
        assertThat(metric.getName()).isEqualTo("opentelemetry_metrics_exporter_dropped_points");
        return metric.getLongSumData().getPoints().stream().collect(onlyElement());
    }

    private static ExponentialHistogramPointData export(ExponentialHistogramDeltaExporter exporter, InMemoryMetricExporter sink, MetricData metric)
    {
        sink.reset();
        assertThat(exporter.export(List.of(metric)).join(10, SECONDS).isSuccess()).isTrue();
        MetricData exported = sink.getFinishedMetricItems().stream().filter(data -> data.getType() == EXPONENTIAL_HISTOGRAM).collect(onlyElement());
        assertThat(exported.getExponentialHistogramData().getAggregationTemporality()).isEqualTo(DELTA);
        return point(exported);
    }

    private static MetricData metric(ExponentialHistogram histogram, long epochNanos)
    {
        return metric(histogram, epochNanos, Attributes.empty());
    }

    private static MetricData metric(ExponentialHistogram histogram, long epochNanos, Attributes attributes)
    {
        return metric(histogram, 10, epochNanos, attributes);
    }

    private static MetricData metric(ExponentialHistogram histogram, long startEpochNanos, long epochNanos, Attributes attributes)
    {
        ExponentialHistogramSnapshot snapshot = histogram.snapshot();
        ExponentialHistogramPointData point = ExponentialHistogramPointData.create(
                snapshot.scale(),
                snapshot.sum(),
                snapshot.zeroCount(),
                snapshot.count() > 0,
                snapshot.min(),
                snapshot.count() > 0,
                snapshot.max(),
                ExponentialHistogramBuckets.create(snapshot.scale(), snapshot.positiveBuckets().offset(), Arrays.stream(snapshot.positiveBuckets().counts()).boxed().toList()),
                ExponentialHistogramBuckets.create(snapshot.scale(), snapshot.negativeBuckets().offset(), Arrays.stream(snapshot.negativeBuckets().counts()).boxed().toList()),
                startEpochNanos,
                epochNanos,
                attributes,
                List.of());
        return ImmutableMetricData.createExponentialHistogram(
                Resource.empty(), OpenTelemetryMetricProducer.INSTRUMENTATION_SCOPE, "test", "", "ns", ExponentialHistogramData.create(CUMULATIVE, List.of(point)));
    }

    private static MetricData withBucketCounts(MetricData metric, int scale, List<Long> positiveCounts, List<Long> negativeCounts)
    {
        ExponentialHistogramPointData point = point(metric);
        ExponentialHistogramPointData modified = ExponentialHistogramPointData.create(
                scale,
                point.getSum(),
                point.getZeroCount(),
                point.hasMin(),
                point.getMin(),
                point.hasMax(),
                point.getMax(),
                ExponentialHistogramBuckets.create(scale, 0, positiveCounts),
                ExponentialHistogramBuckets.create(scale, 0, negativeCounts),
                point.getStartEpochNanos(),
                point.getEpochNanos(),
                point.getAttributes(),
                point.getExemplars());
        return ImmutableMetricData.createExponentialHistogram(
                metric.getResource(),
                metric.getInstrumentationScopeInfo(),
                metric.getName(),
                metric.getDescription(),
                metric.getUnit(),
                ExponentialHistogramData.create(CUMULATIVE, List.of(modified)));
    }

    private static MetricData withExemplars(MetricData metric, List<DoubleExemplarData> exemplars)
    {
        ExponentialHistogramPointData point = point(metric);
        ExponentialHistogramPointData withExemplars = ExponentialHistogramPointData.create(
                point.getScale(),
                point.getSum(),
                point.getZeroCount(),
                point.hasMin(),
                point.getMin(),
                point.hasMax(),
                point.getMax(),
                point.getPositiveBuckets(),
                point.getNegativeBuckets(),
                point.getStartEpochNanos(),
                point.getEpochNanos(),
                point.getAttributes(),
                exemplars);
        return ImmutableMetricData.createExponentialHistogram(
                metric.getResource(),
                InstrumentationScopeInfo.create("other"),
                metric.getName(),
                metric.getDescription(),
                metric.getUnit(),
                ExponentialHistogramData.create(CUMULATIVE, List.of(withExemplars)));
    }

    private static ExponentialHistogramPointData point(MetricData metric)
    {
        return metric.getExponentialHistogramData().getPoints().stream().collect(onlyElement());
    }
}
