package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.node.NodeInfo;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.DeploymentAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOpenTelemetryModule
{
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>()
    {
        @Override
        public Iterable<String> keys(Map<String, String> carrier)
        {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key)
        {
            return (carrier == null) ? null : carrier.get(key);
        }
    };

    @Test
    void testNoopProviders()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"))
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);
        Meter meter = injector.getInstance(Meter.class);

        assertThat(tracer.getClass().getName())
                .isEqualTo("io.opentelemetry.api.trace.DefaultTracer");
        assertThat(meter.getClass().getName())
                .isEqualTo("io.opentelemetry.api.metrics.DefaultMeter");

        tracer.spanBuilder("my-span").startSpan().end();
        meter.counterBuilder("my-counter").build().add(123);
    }

    @Test
    void testExporterEnabled()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                new OpenTelemetryExporterModule())
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);
        Meter meter = injector.getInstance(Meter.class);

        assertThat(tracer.getClass().getName())
                .isEqualTo("io.opentelemetry.sdk.trace.SdkTracer");
        assertThat(meter.getClass().getName())
                .isEqualTo("io.opentelemetry.sdk.metrics.SdkMeter");

        tracer.spanBuilder("my-span").startSpan().end();
        meter.counterBuilder("my-counter").build().add(123);
    }

    @Test
    void testCustomSpanProcessor()
    {
        @SuppressWarnings("resource")
        InMemorySpanExporter exporter = InMemorySpanExporter.create();

        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                        .toInstance(SimpleSpanProcessor.create(exporter)))
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);
        String environment = injector.getInstance(NodeInfo.class).getEnvironment();

        tracer.spanBuilder("my-span")
                .setAttribute("my-attribute", "my-value")
                .startSpan().end();

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData span = exporter.getFinishedSpanItems().stream().collect(onlyElement());

        assertThat(span.getName()).isEqualTo("my-span");

        assertThat(span.getAttributes().asMap()).isEqualTo(Attributes.builder()
                .put(stringKey("my-attribute"), "my-value")
                .build().asMap());

        assertThat(span.getResource().getAttributes().asMap()).contains(
                entry(ServiceAttributes.SERVICE_NAME, "testService"),
                entry(ServiceAttributes.SERVICE_VERSION, "testVersion"),
                entry(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, environment));
    }

    @Test
    void testCustomLogRecordProcessor()
    {
        @SuppressWarnings("resource")
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();

        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, LogRecordProcessor.class).addBinding()
                        .toInstance(SimpleLogRecordProcessor.create(exporter)))
                .quiet()
                .initialize();

        OpenTelemetry openTelemetry = injector.getInstance(OpenTelemetry.class);
        String environment = injector.getInstance(NodeInfo.class).getEnvironment();

        openTelemetry.getLogsBridge()
                .get("test-logger")
                .logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("test log message")
                .emit();

        assertThat(exporter.getFinishedLogRecordItems()).hasSize(1);
        LogRecordData logRecord = exporter.getFinishedLogRecordItems().stream().collect(onlyElement());

        assertThat(logRecord.getBodyValue().asString()).isEqualTo("test log message");
        assertThat(logRecord.getSeverity()).isEqualTo(Severity.INFO);

        assertThat(logRecord.getResource().getAttributes().asMap()).contains(
                entry(ServiceAttributes.SERVICE_NAME, "testService"),
                entry(ServiceAttributes.SERVICE_VERSION, "testVersion"),
                entry(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, environment));
    }

    @Test
    void testCustomMetricReader()
    {
        @SuppressWarnings("resource")
        InMemoryMetricExporter exporter = InMemoryMetricExporter.create();
        PeriodicMetricReader reader = PeriodicMetricReader.create(exporter);

        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, MetricReader.class).addBinding()
                        .toInstance(reader))
                .quiet()
                .initialize();

        Meter meter = injector.getInstance(Meter.class);
        String environment = injector.getInstance(NodeInfo.class).getEnvironment();

        meter.counterBuilder("my-counter")
                .build()
                .add(123);
        reader.forceFlush();

        assertThat(exporter.getFinishedMetricItems()).hasSize(1);
        MetricData metric = exporter.getFinishedMetricItems().stream().collect(onlyElement());

        assertThat(metric.getName()).isEqualTo("my-counter");

        assertThat(metric.getLongSumData().getPoints()).singleElement().extracting(LongPointData::getValue).isEqualTo(123L);

        assertThat(metric.getResource().getAttributes().asMap()).contains(
                entry(ServiceAttributes.SERVICE_NAME, "testService"),
                entry(ServiceAttributes.SERVICE_VERSION, "testVersion"),
                entry(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, environment));
    }

    @Test
    void testCustomMetricProducer()
    {
        InMemoryMetricReader reader = InMemoryMetricReader.create();
        AtomicBoolean produced = new AtomicBoolean();
        MetricProducer producer = _ -> {
            produced.set(true);
            return List.of();
        };

        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, MetricReader.class).addBinding()
                        .toInstance(reader),
                binder -> newSetBinder(binder, MetricProducer.class).addBinding()
                        .toInstance(producer))
                .quiet()
                .initialize();

        injector.getInstance(Meter.class);
        reader.collectAllMetrics();

        assertThat(produced).isTrue();
    }

    @Test
    void testBaggagePropagation()
    {
        @SuppressWarnings("resource")
        InMemorySpanExporter exporter = InMemorySpanExporter.create();

        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                        .toInstance(SimpleSpanProcessor.create(exporter)))
                .setRequiredConfigurationProperties(ImmutableMap.of("otel.tracing.baggage.allowed-keys", "orderId"))
                .quiet()
                .initialize();

        OpenTelemetry openTelemetry = injector.getInstance(OpenTelemetry.class);
        TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();

        // Baggage must take part in cross-service context propagation
        assertThat(propagator.fields()).contains("baggage");

        // Inject baggage from the current context into outgoing carrier (e.g. HTTP headers)
        Map<String, String> carrier = new HashMap<>();
        Context context = Baggage.builder().put("orderId", "42").build().storeInContext(Context.root());
        propagator.inject(context, carrier, Map::put);
        assertThat(carrier).containsKey("baggage");

        // Extract baggage on the receiving side from the incoming carrier
        Context extracted = propagator.extract(Context.root(), carrier, MAP_GETTER);

        assertThat(Baggage.fromContext(extracted).getEntryValue("orderId")).isEqualTo("42");
    }

    @Test
    void testBaggageAllowlistDropsNonAllowlistedKeysOnInjectAndExtract()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                        .toInstance(SpanProcessor.composite()))
                .setRequiredConfigurationProperties(ImmutableMap.of("otel.tracing.baggage.allowed-keys", "orderId"))
                .quiet()
                .initialize();

        TextMapPropagator propagator = injector.getInstance(OpenTelemetry.class).getPropagators().getTextMapPropagator();

        // A non-allowlisted key can end up in the current baggage at runtime (e.g. added by application
        // code), not only via an inbound request, so it must be stripped on the way out as well - the
        // wire payload itself must never contain it, not just the value observed after a subsequent extract.
        Map<String, String> outboundCarrier = new HashMap<>();
        Context context = Baggage.builder()
                .put("orderId", "42")
                .put("secret", "leaked")
                .build()
                .storeInContext(Context.root());
        propagator.inject(context, outboundCarrier, Map::put);

        assertThat(outboundCarrier.get("baggage"))
                .contains("orderId")
                .doesNotContain("secret", "leaked");

        // A non-allowlisted key arriving from a caller must also be dropped on extract.
        Map<String, String> inboundCarrier = new HashMap<>();
        inboundCarrier.put("baggage", "orderId=99,secret=leaked");
        Baggage extracted = Baggage.fromContext(propagator.extract(Context.root(), inboundCarrier, MAP_GETTER));

        assertThat(extracted.getEntryValue("orderId")).isEqualTo("99");
        assertThat(extracted.getEntryValue("secret")).isNull();
    }

    @Test
    void testBaggageAllowlistSanitizesValues()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new OpenTelemetryModule("testService", "testVersion"),
                binder -> newSetBinder(binder, SpanProcessor.class).addBinding()
                        .toInstance(SpanProcessor.composite()))
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "otel.tracing.baggage.allowed-keys", "orderId,note",
                        "otel.tracing.baggage.max-value-length", "5"))
                .quiet()
                .initialize();

        TextMapPropagator propagator = injector.getInstance(OpenTelemetry.class).getPropagators().getTextMapPropagator();

        Context context = Baggage.builder()
                .put("orderId", "1234567890")
                .put("note", "line1\nline2")
                .build()
                .storeInContext(Context.root());

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        Baggage extracted = Baggage.fromContext(propagator.extract(Context.root(), carrier, MAP_GETTER));

        // value truncated to the configured maximum length
        assertThat(extracted.getEntryValue("orderId")).isEqualTo("12345");
        // a value containing control characters is dropped entirely, not merely truncated, since it
        // could otherwise be used to forge extra log lines or header entries
        assertThat(extracted.getEntryValue("note")).isNull();
    }
}
