package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import static com.proofpoint.event.client.EventJsonSerializer.createEventJsonSerializer;

public class JsonEventWriter
{
    private final ObjectMapper objectMapper;
    private final Set<Class<?>> registeredTypes;

    @Inject
    public JsonEventWriter(ObjectMapper objectMapper, Set<EventTypeMetadata<?>> eventTypes, HttpEventClientConfig config)
    {
        Preconditions.checkNotNull(objectMapper, "objectMapper is null");
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        Preconditions.checkNotNull(config, "config is null");

        this.objectMapper = objectMapper;

        ImmutableSet.Builder<Class<?>> typeRegistrations = ImmutableSet.builder();

        SimpleModule eventModule = new SimpleModule("JsonEvent", new Version(1, 0, 0, null));
        for (EventTypeMetadata<?> eventType : eventTypes) {
            eventModule.addSerializer(createEventJsonSerializer(eventType, config.getJsonVersion()));
            typeRegistrations.add(eventType.getEventClass());
        }
        objectMapper.registerModule(eventModule);
        this.registeredTypes = typeRegistrations.build();
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
                if (!registeredTypes.contains(event.getClass())) {
                    throw new RuntimeException(
                            String.format("Event type %s has not been registered as an event",
                                    event.getClass().getSimpleName()));
                }
                jsonGenerator.writeObject(event);
            }
        });

        jsonGenerator.writeEndArray();
        jsonGenerator.flush();
    }
}
