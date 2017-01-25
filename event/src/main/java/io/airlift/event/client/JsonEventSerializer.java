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
import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

import javax.inject.Inject;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static io.airlift.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static java.util.Objects.requireNonNull;

@Beta
public class JsonEventSerializer
{
    private final Map<Class<?>, JsonSerializer<?>> serializers;

    @Inject
    public JsonEventSerializer(Set<EventTypeMetadata<?>> eventTypes)
    {
        requireNonNull(eventTypes, "eventTypes is null");

        ImmutableMap.Builder<Class<?>, JsonSerializer<?>> map = ImmutableMap.builder();
        for (EventTypeMetadata<?> eventType : eventTypes) {
            map.put(eventType.getEventClass(), new EventJsonSerializer<>(eventType));
        }
        this.serializers = map.build();
    }

    public JsonEventSerializer(Class<?>... eventClasses)
    {
        this(getValidEventTypeMetaDataSet(eventClasses));
    }

    public <T> void serialize(T event, JsonGenerator jsonGenerator)
            throws IOException
    {
        requireNonNull(event, "event is null");
        requireNonNull(jsonGenerator, "jsonGenerator is null");

        JsonSerializer<T> serializer = getSerializer(event);
        if (serializer == null) {
            throw new InvalidEventException("Event class [%s] has not been registered as an event", event.getClass().getName());
        }

        serializer.serialize(event, jsonGenerator, null);
    }

    @SuppressWarnings("unchecked")
    private <T> JsonSerializer<T> getSerializer(T event)
    {
        return (JsonSerializer<T>) serializers.get(event.getClass());
    }
}
