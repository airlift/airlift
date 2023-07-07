package io.airlift.tracing;

import io.airlift.bootstrap.Bootstrap;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class TestTracingModule
{
    @Test
    public void testCreateNoopTracing()
    {
        Bootstrap bootstrap = new Bootstrap(new TracingModule("airlift", "0.1"));
        OpenTelemetry instance = bootstrap
                .doNotInitializeLogging()
                .initialize()
                .getInstance(OpenTelemetry.class);

        assertThat(instance).isEqualTo(OpenTelemetry.noop());
    }

    @Test
    public void testCreateTracingWithDefaultConfig()
    {
        Bootstrap bootstrap = new Bootstrap(
                new TracingModule("airlift", "0.1"),
                new TestingNodeModule("test"));
        OpenTelemetry instance = bootstrap
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("tracing.enabled", "true")
                .initialize()
                .getInstance(OpenTelemetry.class);

        assertThat(instance).isNotEqualTo(OpenTelemetry.noop());
        assertThat(instance).isInstanceOf(OpenTelemetrySdk.class);
        assertThat(instance.toString())
                .contains("BatchSpanProcessor{spanExporter=io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter");
    }

    @Test
    public void testCreateTracingWithCustomSpanProcessor()
    {
        Bootstrap bootstrap = new Bootstrap(
                new TracingModule(binder -> {
                    binder.bind(SpanProcessor.class).toInstance(SimpleSpanProcessor.create(OtlpHttpSpanExporter.getDefault()));
                }, "airlift", "0.1"),
                new TestingNodeModule("test"));
        OpenTelemetry instance = bootstrap
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("tracing.enabled", "true")
                .initialize()
                .getInstance(OpenTelemetry.class);

        assertThat(instance).isNotEqualTo(OpenTelemetry.noop());
        assertThat(instance).isInstanceOf(OpenTelemetrySdk.class);
        assertThat(instance.toString())
                .contains("SimpleSpanProcessor{spanExporter=io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter");
    }

    @Test
    public void testCreateTracingWillFailWithoutSpanProcessor()
    {
        Bootstrap bootstrap = new Bootstrap(
                new TracingModule(binder -> {}, "airlift", "0.1"),
                new TestingNodeModule("test"));

        assertThatThrownBy(() -> bootstrap
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("tracing.enabled", "true")
                .initialize()
                .getInstance(OpenTelemetry.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Explicit bindings are required and SpanProcessor is not explicitly bound.");
    }
}
