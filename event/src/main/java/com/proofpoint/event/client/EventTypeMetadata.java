package com.proofpoint.event.client;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Primitives;
import org.codehaus.jackson.JsonGenerator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newTreeMap;
import static com.proofpoint.event.client.AnnotationUtils.findAnnotatedMethods;

class EventTypeMetadata<T>
{
    public static Set<EventTypeMetadata<?>> getValidEventTypeMetaDataSet(Class<?>... eventClasses)
    {
        ImmutableSet.Builder<EventTypeMetadata<?>> set = ImmutableSet.builder();
        for (Class<?> eventClass : eventClasses) {
            set.add(getValidEventTypeMetadata(eventClass));
        }
        return set.build();
    }

    public static <T> EventTypeMetadata<T> getValidEventTypeMetadata(Class<T> eventClass)
    {
        EventTypeMetadata<T> metadata = getEventTypeMetadata(eventClass);
        if (!metadata.getErrors().isEmpty()) {
            String errors = Joiner.on('\n').join(metadata.getErrors());
            throw new IllegalArgumentException(String.format("Invalid event class [%s]:%n%s", eventClass.getName(), errors));
        }
        return metadata;
    }

    public static <T> EventTypeMetadata<T> getEventTypeMetadata(Class<T> eventClass)
    {
        return new EventTypeMetadata<T>(eventClass);
    }

    private final Class<T> eventClass;
    private final String typeName;
    private final EventFieldMetadata uuidField;
    private final EventFieldMetadata timestampField;
    private final EventFieldMetadata hostField;
    private final Map<String, EventFieldMetadata> fields;
    private final List<String> errors = newArrayList();

    private EventTypeMetadata(Class<T> eventClass)
    {
        Preconditions.checkNotNull(eventClass, "eventClass is null");

        this.eventClass = eventClass;

        // get type name from annotation or class name
        String typeName = eventClass.getSimpleName();
        if (!eventClass.isAnnotationPresent(EventType.class)) {
            addError("Event class [%s] is not annotated with @%s", eventClass.getName(), EventType.class.getSimpleName());
        }
        else {
            EventType typeAnnotation = eventClass.getAnnotation(EventType.class);
            if (!typeAnnotation.value().isEmpty()) {
                typeName = typeAnnotation.value();
            }
        }
        this.typeName = typeName;

        // build event field metadata
        List<EventFieldMetadata> uuidFields = newArrayList();
        List<EventFieldMetadata> timestampFields = newArrayList();
        List<EventFieldMetadata> hostFields = newArrayList();
        Map<String, EventFieldMetadata> fields = newTreeMap();
        for (Method method : findAnnotatedMethods(eventClass, EventField.class)) {
            // validate method
            if (method.getParameterTypes().length != 0) {
                addError("@%s method [%s] does not have zero parameters", EventField.class.getSimpleName(), method.toGenericString());
                continue;
            }
            EventDataType eventDataType = EventDataType.byType.get(method.getReturnType());
            if (eventDataType == null) {
                addError("@%s method [%s] return type [%s] is not supported", EventField.class.getSimpleName(), method.toGenericString(), method.getReturnType());
                continue;
            }

            EventField eventField = method.getAnnotation(EventField.class);
            String fieldName = eventField.value();
            String v1FieldName = null;
            if (eventField.fieldMapping() != EventField.EventFieldMapping.DATA) {
                // validate special fields
                if (!fieldName.isEmpty()) {
                    addError("@%s method [%s] has a value and non-DATA fieldMapping (%s)", EventField.class.getSimpleName(), method.toGenericString(), eventField.fieldMapping());
                }
                fieldName = eventField.fieldMapping().name().toLowerCase();
            }
            else {
                if (fieldName.isEmpty()) {
                    String methodName = method.getName();
                    if (methodName.length() > 3 && methodName.startsWith("get")) {
                        fieldName = methodName.substring(3);
                    }
                    else if (methodName.length() > 2 && methodName.startsWith("is")) {
                        fieldName = methodName.substring(2);
                    }
                    else {
                        fieldName = methodName;
                    }
                }
                // always lowercase the first letter, even for user specified names
                v1FieldName = fieldName;
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                if (fields.containsKey(fieldName)) {
                    addError("Event class [%s] Multiple methods are annotated for @% field [%s]", eventClass.getName(), EventField.class.getSimpleName(), fieldName);
                    continue;
                }
            }

            EventFieldMetadata eventFieldMetadata = new EventFieldMetadata(fieldName, v1FieldName, method, EventDataType.byType.get(method.getReturnType()));
            switch (eventField.fieldMapping()) {
                case HOST:
                    hostFields.add(eventFieldMetadata);
                    break;
                case TIMESTAMP:
                    timestampFields.add(eventFieldMetadata);
                    break;
                case UUID:
                    uuidFields.add(eventFieldMetadata);
                    break;
                case DATA:
                    fields.put(fieldName, eventFieldMetadata);
                    break;
                default:
                    throw new AssertionError("unhandled fieldMapping type: " + eventField.fieldMapping());
            }
        }

        // find invalid event methods not skipped by findEventMethods()
        for (Class<?> clazz = eventClass; (clazz != null) && !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventField.class)) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        addError("@%s method [%s] is not public", EventField.class.getSimpleName(), method.toGenericString());
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        addError("@%s method [%s] is static", EventField.class.getSimpleName(), method.toGenericString());
                    }
                }
            }
        }

        if (!uuidFields.isEmpty() && uuidFields.size() > 1) {
            addError("Event class [%s] Multiple methods are annotated for @%s(fieldMapping=%s)", eventClass.getName(), EventField.class.getSimpleName(), EventField.EventFieldMapping.UUID);
        }
        this.uuidField = Iterables.getFirst(uuidFields, null);

        if (!timestampFields.isEmpty() && timestampFields.size() > 1) {
            addError("Event class [%s] Multiple methods are annotated for @%s(fieldMapping=%s)", eventClass.getName(), EventField.class.getSimpleName(), EventField.EventFieldMapping.TIMESTAMP);
        }
        this.timestampField = Iterables.getFirst(timestampFields, null);

        if (!hostFields.isEmpty() && hostFields.size() > 1) {
            addError("Event class [%s] Multiple methods are annotated for @%s(fieldMapping=%s)", eventClass.getName(), EventField.class.getSimpleName(), EventField.EventFieldMapping.HOST);
        }
        this.hostField = Iterables.getFirst(hostFields, null);

        this.fields = ImmutableMap.copyOf(fields);

        if (getErrors().isEmpty() && this.fields.isEmpty()) {
            addError("Event class [%s] does not have any @%s annotations", eventClass.getName(), EventField.class.getSimpleName());
        }
    }

    List<String> getErrors()
    {
        return ImmutableList.copyOf(errors);
    }

    public Class<T> getEventClass()
    {
        return eventClass;
    }

    public String getTypeName()
    {
        return typeName;
    }

    public EventFieldMetadata getUuidField()
    {
        return uuidField;
    }

    public EventFieldMetadata getTimestampField()
    {
        return timestampField;
    }

    public EventFieldMetadata getHostField()
    {
        return hostField;
    }

    public Map<String, EventFieldMetadata> getFields()
    {
        return fields;
    }

    public void addError(String format, Object... args)
    {
        String message = String.format(format, args);
        errors.add(message);
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EventTypeMetadata<?> that = (EventTypeMetadata<?>) o;

        if (eventClass != null ? !eventClass.equals(that.eventClass) : that.eventClass != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return eventClass != null ? eventClass.hashCode() : 0;
    }

    static class EventFieldMetadata
    {
        private final String name;
        private final String v1Name;
        private final Method method;
        private final EventDataType eventDataType;

        private EventFieldMetadata(String name, String v1Name, Method method, EventDataType eventDataType)
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

    @SuppressWarnings("UnusedDeclaration")
    static enum EventDataType
    {
        STRING(String.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, String.class);
                        jsonGenerator.writeString((String) value);
                    }
                },

        BOOLEAN(Boolean.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Boolean.class);
                        jsonGenerator.writeBoolean((Boolean) value);
                    }
                },

        BYTE(Byte.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Byte.class);
                        jsonGenerator.writeNumber((Byte) value);
                    }
                },

        SHORT(Short.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Short.class);
                        jsonGenerator.writeNumber((Short) value);
                    }
                },

        INTEGER(Integer.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Integer.class);
                        jsonGenerator.writeNumber((Integer) value);
                    }
                },

        LONG(Long.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Long.class);
                        jsonGenerator.writeNumber((Long) value);
                    }
                },

        FLOAT(Float.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Float.class);
                        jsonGenerator.writeNumber((Float) value);
                    }
                },

        DOUBLE(Double.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Double.class);
                        jsonGenerator.writeNumber((Double) value);
                    }
                },

        BIG_DECIMAL(BigDecimal.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, BigDecimal.class);
                        jsonGenerator.writeNumber((BigDecimal) value);
                    }
                },

        BIG_INTEGER(BigInteger.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, BigInteger.class);
                        jsonGenerator.writeNumber(new BigDecimal((BigInteger) value));
                    }
                },

        DATETIME(DateTime.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, DateTime.class);
                        jsonGenerator.writeString(ISO_DATETIME_FORMAT.print((DateTime) value));
                    }
                },

        ENUM(Enum.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, Enum.class);
                        jsonGenerator.writeString(value.toString());
                    }
                },

        INET_ADDRESS(InetAddress.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, InetAddress.class);
                        jsonGenerator.writeString(((InetAddress) value).getHostAddress());
                    }
                },

        UUID(java.util.UUID.class)
                {
                    public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                            throws IOException
                    {
                        validateFieldValueType(value, java.util.UUID.class);
                        jsonGenerator.writeString(value.toString());
                    }
                };

        private static final DateTimeFormatter ISO_DATETIME_FORMAT = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

        public static final Map<Class<?>, EventDataType> byType;

        static {
            ImmutableMap.Builder<Class<?>, EventDataType> builder = ImmutableMap.builder();
            for (EventDataType eventDataType : EventDataType.values()) {
                Class<?> dataType = eventDataType.getType();
                builder.put(dataType, eventDataType);
                if (Primitives.isWrapperType(dataType)) {
                    builder.put(Primitives.unwrap(dataType), eventDataType);
                }
            }
            byType = builder.build();
        }

        private final Class<?> type;

        private EventDataType(Class<?> type)
        {
            this.type = type;
        }

        public Class<?> getType()
        {
            return type;
        }

        private static void validateFieldValueType(Object value, Class<?> expectedType)
        {
            Preconditions.checkNotNull(value, "value is null");
            Preconditions.checkArgument(expectedType.isInstance(value),
                    "Expected 'value' to be a " + expectedType.getSimpleName() +
                            " but it is a " + value.getClass().getName());
        }

        public abstract void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                throws IOException;
    }
}
