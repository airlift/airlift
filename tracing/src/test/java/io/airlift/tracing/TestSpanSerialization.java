package io.airlift.tracing;

import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.airlift.tracing.SpanSerialization.SpanDeserializer;
import io.airlift.tracing.SpanSerialization.SpanSerializer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSpanSerialization
{
    @Test
    public void testSerialization()
    {
        JsonCodec<Span> codec = createCodec(Span.class);

        String traceId = "414e5e5043a0436f80ad0891b7c2d2da";
        String spanId = "31388bfaf867482b";
        TraceFlags traceFlags = TraceFlags.getSampled();
        TraceState traceState = TraceState.builder()
                .put("abc", "xyz")
                .put("hello", "world")
                .put("test", "oops")
                .build();

        SpanContext context = SpanContext.create(traceId, spanId, traceFlags, traceState);

        String json = codec.toJson(Span.wrap(context));
        SpanContext decoded = codec.fromJson(json).getSpanContext();

        assertThat(json).isEqualTo(
                """
                {
                  "traceparent" : "00-414e5e5043a0436f80ad0891b7c2d2da-31388bfaf867482b-01",
                  "tracestate" : "test=oops,hello=world,abc=xyz"
                }""");

        assertThat(decoded.isValid()).isTrue();
        assertThat(decoded.getTraceId()).isEqualTo(traceId);
        assertThat(decoded.getSpanId()).isEqualTo(spanId);
        assertThat(decoded.getTraceFlags()).isEqualTo(traceFlags);
        assertThat(decoded.getTraceState()).isEqualTo(traceState);
    }

    @Test
    public void testNestedSerialization()
    {
        JsonCodec<NestedSpan> codec = createCodec(NestedSpan.class);

        String traceId = "414e5e5043a0436f80ad0891b7c2d2da";
        String spanId = "31388bfaf867482b";
        TraceFlags traceFlags = TraceFlags.getSampled();
        TraceState traceState = TraceState.builder()
                .put("abc", "xyz")
                .put("hello", "world")
                .put("test", "oops")
                .build();

        SpanContext context = SpanContext.create(traceId, spanId, traceFlags, traceState);

        NestedSpan nested = new NestedSpan(Span.wrap(context), "outer field");
        NestedSpan decoded = codec.fromJson(codec.toJson(nested));

        assertThat(decoded.inner.getSpanContext().isValid()).isTrue();
        assertThat(decoded.inner.getSpanContext().getTraceId()).isEqualTo(traceId);
        assertThat(decoded.inner.getSpanContext().getSpanId()).isEqualTo(spanId);
        assertThat(decoded.inner.getSpanContext().getTraceFlags()).isEqualTo(traceFlags);
        assertThat(decoded.inner.getSpanContext().getTraceState()).isEqualTo(traceState);
    }

    private static <T> JsonCodec<T> createCodec(Class<T> clazz)
    {
        OpenTelemetry openTelemetry = OpenTelemetry.propagating(
                ContextPropagators.create(W3CTraceContextPropagator.getInstance()));

        return new JsonCodecFactory(new JsonMapperProvider()
                .withJsonSerializers(Map.of(Span.class, new SpanSerializer(openTelemetry)))
                .withJsonDeserializers(Map.of(Span.class, new SpanDeserializer(openTelemetry)))
                .get()
                .rebuild()
                // This makes sure that serde correctly handles deserialization for nested POJO
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
                .build())
                .prettyPrint()
                .jsonCodec(clazz);
    }

    public record NestedSpan(Span inner, String outer)
    {
        public NestedSpan
        {
            requireNonNull(inner, "inner is null");
            requireNonNull(outer, "outer is null");
        }
    }
}
