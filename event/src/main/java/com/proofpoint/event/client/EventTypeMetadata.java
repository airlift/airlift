package com.proofpoint.event.client;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newTreeMap;
import static com.proofpoint.event.client.AnnotationUtils.findAnnotatedMethods;
import static com.proofpoint.event.client.EventDataType.getEventDataType;

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
            EventDataType eventDataType = getEventDataType(method.getReturnType());
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
                fieldName = eventField.fieldMapping().getFieldName();
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

            EventFieldMetadata eventFieldMetadata = new EventFieldMetadata(fieldName, v1FieldName, method, getEventDataType(method.getReturnType()));
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
}
