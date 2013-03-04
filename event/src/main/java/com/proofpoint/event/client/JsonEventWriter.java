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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.event.client.EventClient.EventGenerator;

import javax.annotation.Nullable;
import javax.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

class JsonEventWriter
{
    private final NodeInfo nodeInfo;
    private final JsonFactory jsonFactory;
    private final Map<Class<?>, EventTypeMetadata<?>> metadataMap;

    @Inject
    public JsonEventWriter(NodeInfo nodeInfo, Set<EventTypeMetadata<?>> eventTypes)
    {
        this.nodeInfo = checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(eventTypes, "eventTypes is null");

        this.jsonFactory = new JsonFactory();

        ImmutableMap.Builder<Class<?>, EventTypeMetadata<?>> metadataBuilder = ImmutableMap.builder();

        for (EventTypeMetadata<?> eventType : eventTypes) {
            metadataBuilder.put(eventType.getEventClass(), eventType);
        }
        this.metadataMap = metadataBuilder.build();
    }

    public <T> void writeEvents(EventGenerator<T> events, @Nullable final String token, OutputStream out)
            throws IOException
    {
        checkNotNull(events, "events is null");
        checkNotNull(out, "out is null");

        final JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);

        jsonGenerator.writeStartArray();

        events.generate(new EventClient.EventPoster<T>()
        {
            @Override
            public void post(T event)
                    throws IOException
            {
                JsonSerializer<T> serializer = getSerializer(event, token);
                if (serializer == null) {
                    throw new InvalidEventException("Event class [%s] has not been registered as an event", event.getClass().getName());
                }

                serializer.serialize(event, jsonGenerator, null);
            }
        });

        jsonGenerator.writeEndArray();
        jsonGenerator.flush();
    }

    @SuppressWarnings("unchecked")
    private <T> JsonSerializer<T> getSerializer(T event, @Nullable String token)
    {
        return new EventJsonSerializer<>(nodeInfo, token,
                (EventTypeMetadata<T>) metadataMap.get(event.getClass()));
    }
}
