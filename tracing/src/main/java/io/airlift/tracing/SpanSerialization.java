package io.airlift.tracing;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.inject.Inject;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.io.IOException;
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
        public void serialize(Span span, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException
        {
            Context context = Context.root().with(span);
            Map<String, String> map = new LinkedHashMap<>();
            propagator.inject(context, map, Map::put);
            jsonGenerator.writeObject(map);
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
                throws IOException
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
