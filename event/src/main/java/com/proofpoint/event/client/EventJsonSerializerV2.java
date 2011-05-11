package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.proofpoint.event.client.EventTypeMetadata.EventFieldMetadata;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

class EventJsonSerializerV2<T> extends JsonSerializer<T>
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
        } else {
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
            writeJsonField(eventTypeMetadata.getUuidField(), jsonGenerator, event);
        } else {
            jsonGenerator.writeStringField("uuid", UUID.randomUUID().toString());
        }

        if (eventTypeMetadata.getHostField() != null) {
            writeJsonField(eventTypeMetadata.getHostField(), jsonGenerator, event);
        } else {
            jsonGenerator.writeStringField("host", hostName);
        }

        if (eventTypeMetadata.getTimestampField() != null) {
            writeJsonField(eventTypeMetadata.getTimestampField(), jsonGenerator, event);
        }
        else {
            jsonGenerator.writeStringField("timestamp", ISODateTimeFormat.dateTime().print(new DateTime().withZone(DateTimeZone.UTC)));
        }

        jsonGenerator.writeObjectFieldStart("data");
        for (EventFieldMetadata field : eventTypeMetadata.getFields().values()) {
            writeJsonField(field, jsonGenerator, event);
        }
        jsonGenerator.writeEndObject();

        jsonGenerator.writeEndObject();
        jsonGenerator.flush();
    }

    private void writeJsonField(EventFieldMetadata fieldMetadata, JsonGenerator jsonGenerator, Object event)
            throws IOException
    {
        jsonGenerator.writeFieldName(fieldMetadata.getName());
        fieldMetadata.writeFieldValue(jsonGenerator, event);
    }
}
