/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.event.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class EventJsonSerializer<T>
        extends JsonSerializer<T>
{
    private final EventTypeMetadata<T> eventTypeMetadata;
    private final String hostName;

    public EventJsonSerializer(EventTypeMetadata<T> eventTypeMetadata)
    {
        requireNonNull(eventTypeMetadata, "eventTypeMetadata is null");

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
            EventDataType.INSTANT.writeFieldValue(jsonGenerator, Instant.now());
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
