package io.airlift.tracing;

import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.ObjectMapperProvider;
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

import java.util.Map;

import static io.airlift.json.ObjectMapperProvider.toObjectMapperProvider;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSpanSerialization
{
    @Test
    public void testSerialization()
    {
        JsonCodec<Span> codec = createSpanJsonCodec();

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

        assertThat(json).isEqualTo("""
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

    private static JsonCodec<Span> createSpanJsonCodec()
    {
        OpenTelemetry openTelemetry = OpenTelemetry.propagating(
                ContextPropagators.create(W3CTraceContextPropagator.getInstance()));

        return new JsonCodecFactory(toObjectMapperProvider(new ObjectMapperProvider()
                .withJsonSerializers(Map.of(Span.class, new SpanSerializer(openTelemetry)))
                .withJsonDeserializers(Map.of(Span.class, new SpanDeserializer(openTelemetry)))))
                .prettyPrint()
                .jsonCodec(Span.class);
    }
}
