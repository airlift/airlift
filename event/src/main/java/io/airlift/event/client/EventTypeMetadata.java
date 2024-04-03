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

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import io.airlift.event.client.EventField.EventFieldMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.event.client.AnnotationUtils.findAnnotatedMethods;
import static io.airlift.event.client.EventDataType.getEventDataType;
import static io.airlift.event.client.EventFieldMetadata.ContainerType;
import static io.airlift.event.client.TypeParameterUtils.getTypeParameters;
import static java.util.Objects.requireNonNull;

public final class EventTypeMetadata<T>
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
        return new EventTypeMetadata<>(eventClass, new ArrayList<>(), new HashMap<>(), false);
    }

    public static <T> EventTypeMetadata<T> getEventTypeMetadataNested(Class<T> eventClass)
    {
        return new EventTypeMetadata<>(eventClass, new ArrayList<>(), new HashMap<>(), true);
    }

    private final Class<T> eventClass;
    private final String typeName;
    private final EventFieldMetadata uuidField;
    private final EventFieldMetadata timestampField;
    private final EventFieldMetadata hostField;
    private final SortedMap<String, EventFieldMetadata> fields;
    private final List<String> errors;

    private EventTypeMetadata(Class<T> eventClass, List<String> errors, Map<Class<?>, EventTypeMetadata<?>> metadataClasses, boolean nestedEvent)
    {
        requireNonNull(eventClass, "eventClass is null");
        requireNonNull(errors, "errors is null");
        requireNonNull(metadataClasses, "metadataClasses is null");
        checkState(!metadataClasses.containsKey(eventClass), "metadataClasses contains eventClass");

        this.eventClass = eventClass;
        this.errors = errors;

        // handle cycles in the object graph
        // these values must not be used until after construction
        metadataClasses.put(eventClass, this);

        // get type name from annotation
        this.typeName = extractTypeName(eventClass, nestedEvent);

        // build event field metadata
        Multimap<EventFieldMapping, EventFieldMetadata> specialFields = ArrayListMultimap.create();
        Map<String, EventFieldMetadata> fields = new TreeMap<>();

        for (Method method : findAnnotatedMethods(eventClass, EventField.class)) {
            // validate method
            if (method.getParameterTypes().length != 0) {
                addMethodError(method, "does not have zero parameters");
                continue;
            }

            // allow accessing public methods in private event classes
            method.setAccessible(true);

            Class<?> dataType = method.getReturnType();
            Optional<ContainerType> containerType = Optional.empty();

            // extract container type and replace data type with it
            if (isIterable(dataType)) {
                dataType = extractIterableType(method);
                containerType = Optional.of(ContainerType.ITERABLE);
            }
            else if (isMap(dataType)) {
                dataType = extractMapType(method, Map.class);
                containerType = Optional.of(ContainerType.MAP);
            }
            else if (isMultimap(dataType)) {
                dataType = extractMapType(method, Multimap.class);
                containerType = Optional.of(ContainerType.MULTIMAP);
            }

            if (dataType == null) {
                continue;
            }

            Optional<EventDataType> eventDataType = Optional.empty();
            Optional<EventTypeMetadata<?>> nestedType = Optional.empty();

            if (isNestedEvent(dataType)) {
                nestedType = Optional.of(getNestedEventTypeMetadata(dataType, metadataClasses));
            }
            else {
                eventDataType = Optional.ofNullable(getEventDataType(dataType));
                if (eventDataType.isEmpty()) {
                    Object typeSource = containerType.map(Object.class::cast).orElse("return");
                    addMethodError(method, "%s type [%s] is not supported", typeSource, dataType);
                    continue;
                }
            }

            EventField eventField = method.getAnnotation(EventField.class);
            String fieldName = eventField.value();

            if (eventField.fieldMapping() != EventFieldMapping.DATA) {
                // validate special fields
                if (containerType.isPresent()) {
                    addMethodError(method, "non-DATA fieldMapping (%s) not allowed for %s", eventField.fieldMapping(), containerType.orElseThrow());
                    continue;
                }
                if (nestedEvent) {
                    addMethodError(method, "non-DATA fieldMapping (%s) not allowed for nested event", eventField.fieldMapping());
                    continue;
                }
                if (!fieldName.isEmpty()) {
                    addMethodError(method, "has a value and non-DATA fieldMapping (%s)", eventField.fieldMapping());
                    continue;
                }
                fieldName = eventField.fieldMapping().getFieldName();
            }
            else {
                if (fieldName.isEmpty()) {
                    fieldName = extractNameFromGetter(method);
                }
                if (!isValidFieldName(fieldName)) {
                    addMethodError(method, "Field name is invalid [%s]", fieldName);
                    continue;
                }
                if (fields.containsKey(fieldName)) {
                    addClassError("Multiple methods are annotated for @X field [%s]", fieldName);
                    continue;
                }
            }

            EventFieldMetadata eventFieldMetadata = new EventFieldMetadata(fieldName, method, eventDataType, nestedType, containerType);
            if (eventField.fieldMapping() == EventFieldMapping.DATA) {
                fields.put(fieldName, eventFieldMetadata);
            }
            else {
                specialFields.put(eventField.fieldMapping(), eventFieldMetadata);
            }
        }

        findInvalidMethods(eventClass);

        for (Map.Entry<EventFieldMapping, Collection<EventFieldMetadata>> entry : specialFields.asMap().entrySet()) {
            if (entry.getValue().size() > 1) {
                addClassError("Multiple methods are annotated for @X(fieldMapping=%s)", entry.getValue());
            }
        }

        this.uuidField = specialFields.get(EventFieldMapping.UUID).stream().findFirst().orElse(null);
        this.timestampField = specialFields.get(EventFieldMapping.TIMESTAMP).stream().findFirst().orElse(null);
        this.hostField = specialFields.get(EventFieldMapping.HOST).stream().findFirst().orElse(null);
        this.fields = ImmutableSortedMap.copyOf(fields);

        if (getErrors().isEmpty() && this.fields.isEmpty()) {
            addClassError("does not have any @X annotations");
        }
    }

    private String extractTypeName(Class<T> eventClass, boolean nestedEvent)
    {
        EventType typeAnnotation = eventClass.getAnnotation(EventType.class);
        if (typeAnnotation == null) {
            addClassError("is not annotated with @%s", EventType.class.getSimpleName());
            return null;
        }
        String typeName = typeAnnotation.value();
        if (nestedEvent && typeName.isEmpty()) {
            return eventClass.getSimpleName();
        }
        else if (typeName.isEmpty()) {
            addClassError("does not specify an event name");
        }
        else if (!isValidEventName(typeName)) {
            addClassError("Event name is invalid [%s]", typeName);
        }
        return typeName;
    }

    private Class<?> extractIterableType(Method method)
    {
        Type[] types = getTypeParameters(Iterable.class, method.getGenericReturnType());
        if ((types == null) || (types.length != 1)) {
            addMethodError(method, "Unable to get type parameter for iterable [%s]", method.getGenericReturnType());
            return null;
        }
        Type type = types[0];
        if (!(type instanceof Class)) {
            addMethodError(method, "Iterable type parameter [%s] must be an exact type", type);
            return null;
        }
        if (isIterable((Class<?>) type)) {
            addMethodError(method, "Iterable of iterable is not supported");
            return null;
        }
        return (Class<?>) type;
    }

    private Class<?> extractMapType(Method method, Class<?> mapClass)
    {
        String className = mapClass.getSimpleName();
        Type[] types = getTypeParameters(mapClass, method.getGenericReturnType());
        if ((types == null) || (types.length != 2)) {
            addMethodError(method, "Unable to get type parameter for %s [%s]", className, method.getGenericReturnType());
            return null;
        }
        Type keyType = types[0];
        Type valueType = types[1];
        if (!(keyType instanceof Class)) {
            addMethodError(method, "%s key type parameter [%s] must be an exact type", className, keyType);
            return null;
        }
        if (!(valueType instanceof Class)) {
            addMethodError(method, "%s value type parameter [%s] must be an exact type", className, valueType);
            return null;
        }
        if (!isString((Class<?>) keyType)) {
            addMethodError(method, "%s key type parameter [%s] must be a String", className, keyType);
        }
        if (isIterable((Class<?>) valueType)) {
            addMethodError(method, "%s value type parameter [%s] cannot be iterable", className, valueType);
            return null;
        }
        return (Class<?>) valueType;
    }

    @SuppressWarnings("unchecked")
    private EventTypeMetadata<?> getNestedEventTypeMetadata(Class<?> eventClass, Map<Class<?>, EventTypeMetadata<?>> metadataClasses)
    {
        EventTypeMetadata<?> metadata = metadataClasses.get(eventClass);
        if (metadata != null) {
            return metadata;
        }

        // the constructor adds itself to the list of classes
        return new EventTypeMetadata(eventClass, errors, metadataClasses, true);
    }

    private void findInvalidMethods(Class<T> eventClass)
    {
        // find invalid methods that were skipped by findAnnotatedMethods()
        for (Class<?> clazz = eventClass; clazz != null; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventField.class)) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        addMethodError(method, "is not public");
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        addMethodError(method, "is static");
                    }
                }
            }
        }
    }

    private static String extractNameFromGetter(Method method)
    {
        String name = method.getName();
        if (name.length() > 3 && name.startsWith("get")) {
            return lowerCaseFirstCharacter(name.substring(3));
        }
        if (name.length() > 2 && name.startsWith("is")) {
            return lowerCaseFirstCharacter(name.substring(2));
        }
        return name;
    }

    private static String lowerCaseFirstCharacter(String s)
    {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    private static boolean isString(Class<?> type)
    {
        return String.class.isAssignableFrom(type);
    }

    private static boolean isIterable(Class<?> type)
    {
        return Iterable.class.isAssignableFrom(type);
    }

    private static boolean isMap(Class<?> type)
    {
        return Map.class.isAssignableFrom(type);
    }

    private static boolean isMultimap(Class<?> type)
    {
        return Multimap.class.isAssignableFrom(type);
    }

    private static boolean isNestedEvent(Class<?> type)
    {
        return type.isAnnotationPresent(EventType.class);
    }

    private static boolean isValidFieldName(String name)
    {
        return name.matches("[a-z][A-Za-z0-9]*");
    }

    private static boolean isValidEventName(String name)
    {
        return name.matches("[A-Z][A-Za-z0-9]*");
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

    public List<EventFieldMetadata> getFields()
    {
        return ImmutableList.copyOf(fields.values());
    }

    public EventFieldMetadata getField(String fieldName)
    {
        return fields.get(fieldName);
    }

    @FormatMethod
    public void addMethodError(Method method, @FormatString String format, Object... args)
    {
        String prefix = String.format("@X method [%s] ", method.toGenericString());
        addClassError(prefix + String.format(format, args));
    }

    public void addClassError(String message)
    {
        errors.add(message);
    }

    @FormatMethod
    public void addClassError(@FormatString String format, Object... args)
    {
        String message = String.format(format, args);
        message = String.format("Event class [%s] %s", eventClass, message);
        message = message.replace("@X", EventField.class.getSimpleName());
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
