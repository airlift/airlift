package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

class EventJsonSerializerV1<T>
        extends JsonSerializer<T>
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
        Preconditions.checkState(eventTypeMetadata.getHostField() == null, "custom host field not supported for JSON V1");
        Preconditions.checkState(eventTypeMetadata.getTimestampField() == null, "custom timestamp field not supported for JSON V1");
        this.eventTypeMetadata = eventTypeMetadata;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to determine local host name");
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
        jsonGenerator.writeStringField("host", hostName);
        jsonGenerator.writeNumberField("timestamp", System.currentTimeMillis());

        jsonGenerator.writeArrayFieldStart("data");
        for (EventFieldMetadata field : eventTypeMetadata.getFields()) {
            field.writeFieldV1(jsonGenerator, event);
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }
}
