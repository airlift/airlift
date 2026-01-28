package io.airlift.tracing;

import com.google.inject.Inject;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public final class SpanSerialization
{
    private SpanSerialization() {}

    public static class SpanSerializer
            extends StdSerializer<Span>
    {
        private final TextMapPropagator propagator;

        @Inject
        public SpanSerializer(OpenTelemetry openTelemetry)
        {
            super(Span.class);
            this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        }

        @SuppressWarnings("DataFlowIssue")
        @Override
        public void serialize(Span span, JsonGenerator jsonGenerator, SerializationContext serializationContext)
        {
            Context context = Context.root().with(span);
            Map<String, String> map = new LinkedHashMap<>();
            propagator.inject(context, map, Map::put);
            jsonGenerator.writePOJO(map);
        }
    }

    public static class SpanDeserializer
            extends StdDeserializer<Span>
    {
        private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};
        private final TextMapPropagator propagator;

        @Inject
        public SpanDeserializer(OpenTelemetry openTelemetry)
        {
            super(Span.class);
            this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        }

        @Override
        public Span deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        {
            Map<String, String> map = jsonParser.readValueAs(MAP_TYPE);
            Context context = propagator.extract(Context.root(), map, MapTextMapGetter.INSTANCE);
            return Span.fromContext(context);
        }

        private static class MapTextMapGetter
                implements TextMapGetter<Map<String, String>>
        {
            public static final MapTextMapGetter INSTANCE = new MapTextMapGetter();

            @Override
            public Iterable<String> keys(Map<String, String> map)
            {
                return map.keySet();
            }

            @Override
            public String get(Map<String, String> map, String key)
            {
                return requireNonNull(map).get(key);
            }
        }
    }
}
