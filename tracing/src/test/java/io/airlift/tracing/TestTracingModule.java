package io.airlift.tracing;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.node.NodeInfo;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.DeploymentIncubatingAttributes;
import org.junit.jupiter.api.Test;

public class TestTracingModule {
    @Test
    void testNoopTracing() {
        Injector injector = new Bootstrap(new TestingNodeModule(), new TracingModule("testService", "testVersion"))
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);

        assertThat(tracer.getClass().getName())
                .isEqualTo(Tracing.noopTracer().getClass().getName());

        tracer.spanBuilder("my-span").startSpan().end();
    }

    @Test
    void testTracingEnabled() {
        Injector injector = new Bootstrap(new TestingNodeModule(), new TracingModule("testService", "testVersion"))
                .setRequiredConfigurationProperty("tracing.enabled", "true")
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);

        assertThat(tracer.getClass().getName())
                .isNotEqualTo(Tracing.noopTracer().getClass().getName());

        tracer.spanBuilder("my-span").startSpan().end();
    }

    @Test
    void testCustomSpanProcessor() {
        @SuppressWarnings("resource")
        InMemorySpanExporter exporter = InMemorySpanExporter.create();

        Injector injector = new Bootstrap(
                        new TestingNodeModule(),
                        new TracingModule("testService", "testVersion"),
                        binder -> newSetBinder(binder, SpanProcessor.class)
                                .addBinding()
                                .toInstance(SimpleSpanProcessor.create(exporter)))
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);
        String environment = injector.getInstance(NodeInfo.class).getEnvironment();

        tracer.spanBuilder("my-span")
                .setAttribute("my-attribute", "my-value")
                .startSpan()
                .end();

        assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        SpanData span = exporter.getFinishedSpanItems().stream().collect(onlyElement());

        assertThat(span.getName()).isEqualTo("my-span");

        assertThat(span.getAttributes().asMap())
                .isEqualTo(Attributes.builder()
                        .put(stringKey("my-attribute"), "my-value")
                        .build()
                        .asMap());

        assertThat(span.getResource().getAttributes().asMap())
                .contains(
                        entry(ServiceAttributes.SERVICE_NAME, "testService"),
                        entry(ServiceAttributes.SERVICE_VERSION, "testVersion"),
                        entry(DeploymentIncubatingAttributes.DEPLOYMENT_ENVIRONMENT_NAME, environment));
    }
}
