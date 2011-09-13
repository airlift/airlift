package com.proofpoint.event.client;

import org.codehaus.jackson.JsonGenerator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;

class EventFieldMetadata
{
    public static final Comparator<EventFieldMetadata> NAME_COMPARATOR = new Comparator<EventFieldMetadata>()
    {
        public int compare(EventFieldMetadata a, EventFieldMetadata b)
        {
            return a.name.compareTo(b.name);
        }
    };

    private final String name;
    private final String v1Name;
    private final Method method;
    private final EventDataType eventDataType;

    EventFieldMetadata(String name, String v1Name, Method method, EventDataType eventDataType)
    {
        this.name = name;
        this.v1Name = v1Name;
        this.method = method;
        this.eventDataType = eventDataType;
    }

    private Object getValue(Object event)
            throws InvalidEventException
    {
        try {
            return method.invoke(event);
        }
        catch (IllegalAccessException e) {
            throw new InvalidEventException(e, "Unexpected exception reading event field %s", name);
        }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                cause = e;
            }
            throw new InvalidEventException(cause,
                    "Unable to get value of event field %s: Exception occurred while invoking [%s]",
                    name,
                    method.toGenericString());
        }
    }

    public void writeField(JsonGenerator jsonGenerator, Object event)
            throws IOException
    {
        Object value = getValue(event);
        if (value != null) {
            jsonGenerator.writeFieldName(name);
            eventDataType.writeFieldValue(jsonGenerator, value);
        }
    }

    public void writeFieldV1(JsonGenerator jsonGenerator, Object event)
            throws IOException
    {
        Object value = getValue(event);
        if (value != null) {
            jsonGenerator.writeStringField("name", v1Name);
            jsonGenerator.writeFieldName("value");
            eventDataType.writeFieldValue(jsonGenerator, value);
        }
    }
}
