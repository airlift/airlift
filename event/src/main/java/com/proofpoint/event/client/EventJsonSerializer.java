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
package com.proofpoint.event.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.proofpoint.node.NodeInfo;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

class EventJsonSerializer<T>
        extends JsonSerializer<T>
{

    private final String token;
    private final EventTypeMetadata<T> eventTypeMetadata;
    private final String hostName;

    public EventJsonSerializer(NodeInfo nodeInfo, @Nullable String token, EventTypeMetadata<T> eventTypeMetadata)
    {
        this.token = token;
        this.eventTypeMetadata = checkNotNull(eventTypeMetadata, "eventTypeMetadata is null");
        if (eventTypeMetadata.getHostField() == null) {
            hostName = nodeInfo.getInternalHostname();
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

        if (eventTypeMetadata.getTraceTokenField() != null) {
            eventTypeMetadata.getTraceTokenField().writeField(jsonGenerator, event);
        }
        else if (token != null) {
            jsonGenerator.writeFieldName("traceToken");
            EventDataType.STRING.writeFieldValue(jsonGenerator, token);
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
