package io.airlift.opentelemetry;

import com.sun.net.httpserver.HttpServer;
import io.airlift.stats.ExponentialHistogram;
import io.airlift.stats.ExponentialHistogram.ExponentialHistogramSnapshot;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramBuckets;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData;
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.time.TestClock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.opentelemetry.sdk.common.export.MemoryMode.REUSABLE_DATA;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA;
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
                assertThat(sink.getFinishedMetricItems()).isEmpty();
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
                .setEndpoint("http://localhost:" + server.getAddress().getPort() + "/v1/metrics");
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

    private static ExponentialHistogramPointData export(ExponentialHistogramDeltaExporter exporter, InMemoryMetricExporter sink, MetricData metric)
    {
        sink.reset();
        assertThat(exporter.export(List.of(metric)).join(10, SECONDS).isSuccess()).isTrue();
        MetricData exported = sink.getFinishedMetricItems().stream().collect(onlyElement());
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
