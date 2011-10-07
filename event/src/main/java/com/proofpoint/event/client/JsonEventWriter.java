package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import static com.proofpoint.event.client.EventJsonSerializer.createEventJsonSerializer;

public class JsonEventWriter
{
    private final ObjectMapper objectMapper;
    private final Map<Class<?>, JsonSerializer> serializers;

    @Inject
    public JsonEventWriter(ObjectMapper objectMapper, Set<EventTypeMetadata<?>> eventTypes, HttpEventClientConfig config)
    {
        Preconditions.checkNotNull(objectMapper, "objectMapper is null");
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        Preconditions.checkNotNull(config, "config is null");

        this.objectMapper = objectMapper;

        ImmutableMap.Builder<Class<?>, JsonSerializer> serializerBuilder = ImmutableMap.builder();

        for (EventTypeMetadata<?> eventType : eventTypes) {
            serializerBuilder.put(eventType.getEventClass(), createEventJsonSerializer(eventType, config.getJsonVersion()));
        }
        this.serializers = serializerBuilder.build();
    }

    public <T> void writeEvents(EventClient.EventGenerator<T> events, OutputStream out)
            throws IOException
    {
        Preconditions.checkNotNull(events, "events is null");
        Preconditions.checkNotNull(out, "out is null");

        JsonFactory jsonFactory = objectMapper.getJsonFactory();
        final JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);

        jsonGenerator.writeStartArray();

        events.generate(new EventClient.EventPoster<T>()
        {
            @Override
            public void post(T event)
                    throws IOException
            {
                JsonSerializer<T> serializer = serializers.get(event.getClass());
                if (serializer == null) {
                    throw new InvalidEventException("Event class [%s] has not been registered as an event", event.getClass().getName());
                }

                serializer.serialize(event, jsonGenerator, null);
            }
        });

        jsonGenerator.writeEndArray();
        jsonGenerator.flush();
    }
}
