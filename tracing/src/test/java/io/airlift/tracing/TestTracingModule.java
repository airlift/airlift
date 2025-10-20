package io.airlift.tracing;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTracingModule
{
    @Test
    void testNoopTracing()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new TracingModule("testService", "testVersion"))
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);

        assertThat(tracer.getClass().getName())
                .isEqualTo(Tracing.noopTracer().getClass().getName());

        tracer.spanBuilder("my-span").startSpan().end();
    }

    @Test
    void testTracingEnabled()
    {
        Injector injector = new Bootstrap(
                new TestingNodeModule(),
                new TracingModule("testService", "testVersion"))
                .setRequiredConfigurationProperty("tracing.enabled", "true")
                .quiet()
                .initialize();

        Tracer tracer = injector.getInstance(Tracer.class);

        assertThat(tracer.getClass().getName())
                .isNotEqualTo(Tracing.noopTracer().getClass().getName());

        tracer.spanBuilder("my-span").startSpan().end();
    }
}
