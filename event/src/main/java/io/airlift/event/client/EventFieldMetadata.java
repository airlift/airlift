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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.codehaus.jackson.JsonGenerator;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Objects.firstNonNull;
import static io.airlift.event.client.EventDataType.validateFieldValueType;

class EventFieldMetadata
{
    public static final Comparator<EventFieldMetadata> NAME_COMPARATOR = new Comparator<EventFieldMetadata>()
    {
        public int compare(EventFieldMetadata a, EventFieldMetadata b)
        {
            return a.name.compareTo(b.name);
        }
    };

    public static enum ContainerType
    {
        ITERABLE, MAP, MULTIMAP;

        @Override
        public String toString()
        {
            return name().toLowerCase();
        }
    }

    private final String name;
    private final Method method;
    private final EventDataType eventDataType;
    private final EventTypeMetadata<?> nestedType;
    private final ContainerType containerType;

    EventFieldMetadata(String name, Method method, EventDataType eventDataType, EventTypeMetadata<?> nestedType, ContainerType containerType)
    {
        Preconditions.checkArgument((eventDataType != null) || (nestedType != null), "both eventDataType and nestedType are null");
        Preconditions.checkArgument((eventDataType == null) || (nestedType == null), "both eventDataType and nestedType are set");

        this.name = name;
        this.method = method;
        this.eventDataType = eventDataType;
        this.nestedType = nestedType;
        this.containerType = containerType;
    }

    private Object getValue(Object event)
            throws InvalidEventException
    {
        try {
            return method.invoke(event);
        }
        catch (Exception e) {
            throw new InvalidEventException(firstNonNull(e.getCause(), e),
                    "Unable to get value of event field %s: Exception occurred while invoking [%s]", name, method.toGenericString());
        }
    }

    public void writeField(JsonGenerator jsonGenerator, Object event)
            throws IOException
    {
        writeField(jsonGenerator, event, new ArrayDeque<>());
    }

    private void writeField(JsonGenerator jsonGenerator, Object event, Deque<Object> objectStack)
            throws IOException
    {
        Object value = getValue(event);
        if (value != null) {
            jsonGenerator.writeFieldName(name);
            if (containerType == ContainerType.ITERABLE) {
                validateFieldValueType(value, Iterable.class);
                writeArray(jsonGenerator, (Iterable<?>) value, objectStack);
            }
            else if (containerType == ContainerType.MAP) {
                validateFieldValueType(value, Map.class);
                writeMap(jsonGenerator, (Map<?, ?>) value, objectStack);
            }
            else if (containerType == ContainerType.MULTIMAP) {
                validateFieldValueType(value, Multimap.class);
                writeMultimap(jsonGenerator, (Multimap<?, ?>) value, objectStack);
            }
            else {
                writeFieldValue(jsonGenerator, value, objectStack);
            }
        }
    }

    private void writeFieldValue(JsonGenerator jsonGenerator, Object value, Deque<Object> objectStack)
            throws IOException
    {
        if (eventDataType != null) {
            eventDataType.writeFieldValue(jsonGenerator, value);
        }
        else {
            validateFieldValueType(value, nestedType.getEventClass());
            writeObject(jsonGenerator, value, objectStack);
        }
    }

    private void writeArray(JsonGenerator jsonGenerator, Iterable<?> value, Deque<Object> objectStack)
            throws IOException
    {
        jsonGenerator.writeStartArray();
        for (Object item : value) {
            writeFieldValue(jsonGenerator, item, objectStack);
        }
        jsonGenerator.writeEndArray();
    }

    private void writeMap(JsonGenerator jsonGenerator, Map<?, ?> value, Deque<Object> objectStack)
            throws IOException
    {
        jsonGenerator.writeStartObject();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            jsonGenerator.writeFieldName((String) entry.getKey());
            writeFieldValue(jsonGenerator, entry.getValue(), objectStack);
        }
        jsonGenerator.writeEndObject();
    }

    private void writeMultimap(JsonGenerator jsonGenerator, Multimap<?, ?> value, Deque<Object> objectStack)
            throws IOException
    {
        jsonGenerator.writeStartObject();
        for (Map.Entry<?, ? extends Collection<?>> entry : value.asMap().entrySet()) {
            jsonGenerator.writeFieldName((String) entry.getKey());
            writeArray(jsonGenerator, entry.getValue(), objectStack);
        }
        jsonGenerator.writeEndObject();
    }

    private void writeObject(JsonGenerator jsonGenerator, Object value, Deque<Object> objectStack)
            throws IOException
    {
        checkForCycles(value, objectStack);
        objectStack.push(value);
        jsonGenerator.writeStartObject();
        for (EventFieldMetadata field : nestedType.getFields()) {
            field.writeField(jsonGenerator, value, objectStack);
        }
        jsonGenerator.writeEndObject();
        objectStack.pop();
    }

    private static void checkForCycles(Object value, Deque<Object> objectStack)
            throws InvalidEventException
    {
        for (Object o : objectStack) {
            if (value == o) {
                List<Object> path = Lists.reverse(Lists.newArrayList(objectStack));
                throw new InvalidEventException("Cycle detected in event data: %s", path);
            }
        }
    }
}
