package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.joda.time.DateTime;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

class EventJsonSerializerV2<T>
        extends JsonSerializer<T>
{
    private final EventTypeMetadata<T> eventTypeMetadata;
    private final String hostName;

    public static <T> EventJsonSerializerV2<T> createEventJsonSerializer(EventTypeMetadata<T> eventTypeMetadata)
    {
        return new EventJsonSerializerV2<T>(eventTypeMetadata);
    }

    private EventJsonSerializerV2(EventTypeMetadata<T> eventTypeMetadata)
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

        jsonGenerator.writeStringField("type", eventTypeMetadata.getTypeName());

        if (eventTypeMetadata.getUuidField() != null) {
            eventTypeMetadata.getUuidField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("uuid", UUID.randomUUID().toString());
        }

        if (eventTypeMetadata.getHostField() != null) {
            eventTypeMetadata.getHostField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("host", hostName);
        }

        if (eventTypeMetadata.getTimestampField() != null) {
            eventTypeMetadata.getTimestampField().writeField(jsonGenerator, event);
        }
        else {
            jsonGenerator.writeFieldName("timestamp");
            EventDataType.DATETIME.writeFieldValue(jsonGenerator, new DateTime());
        }

        jsonGenerator.writeObjectFieldStart("data");
        for (EventFieldMetadata field : eventTypeMetadata.getFields()) {
            field.writeField(jsonGenerator, event);
        }
        jsonGenerator.writeEndObject();

        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }
}
