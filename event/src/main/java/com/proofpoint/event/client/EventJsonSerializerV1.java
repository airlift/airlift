package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

class EventJsonSerializerV1<T> extends JsonSerializer<T>
{
    private final EventTypeMetadata<T> eventTypeMetadata;
    private final String hostName;

    public static <T> EventJsonSerializerV1<T> createEventJsonSerializer(EventTypeMetadata<T> eventTypeMetadata)
    {
        return new EventJsonSerializerV1<T>(eventTypeMetadata);
    }

    private EventJsonSerializerV1(EventTypeMetadata<T> eventTypeMetadata)
    {
        Preconditions.checkNotNull(eventTypeMetadata, "eventTypeMetadata is null");
        this.eventTypeMetadata = eventTypeMetadata;
        if (eventTypeMetadata.getHostField() == null) {
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unable to determine local host name");
            }
        }
        else {
            hostName = null;
        }
    }

    @Override
    public Class<T> handledType()
    {
        return eventTypeMetadata.getEventClass();
    }

    @Override
    public void serialize(T event, JsonGenerator jsonGenerator, SerializerProvider provider)
            throws IOException
    {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("name", eventTypeMetadata.getTypeName());
        jsonGenerator.writeStringField("type", "metrics"); // todo

        if (eventTypeMetadata.getHostField() != null) {
            writeJsonField(eventTypeMetadata.getHostField(), jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("host", hostName);
        }

        if (eventTypeMetadata.getTimestampField() != null) {
            writeJsonField(eventTypeMetadata.getTimestampField(), jsonGenerator, event);
        }
        else {
            jsonGenerator.writeNumberField("timestamp", System.currentTimeMillis());
        }

        jsonGenerator.writeArrayFieldStart("data");
        for (EventFieldMetadata field : eventTypeMetadata.getFields().values()) {
            jsonGenerator.writeStartObject();
            writeJsonField(field, jsonGenerator, event);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }

    private void writeJsonField(EventFieldMetadata fieldMetadata, JsonGenerator jsonGenerator, Object event)
            throws IOException
    {
        fieldMetadata.writeFieldV1(jsonGenerator, event);
    }
}
