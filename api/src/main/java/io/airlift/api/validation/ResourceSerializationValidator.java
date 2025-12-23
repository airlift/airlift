package io.airlift.api.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiId;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiPolyResource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class ResourceSerializationValidator
{
    private final Map<Type, Object> valueCache = new HashMap<>();
    private final Set<Class<?>> resourceClasses;

    public ResourceSerializationValidator(Set<Class<?>> resourceClasses)
    {
        this.resourceClasses = requireNonNull(resourceClasses, "resourceClasses is null");
    }

    public void validateSerialization(ObjectMapper objectMapper)
    {
        ValidationContext validationContext = new ValidationContext();
        resourceClasses.forEach(resourceClass -> validateSerialization(validationContext, objectMapper, resourceClass));
    }

    private void validateSerialization(ValidationContext validationContext, ObjectMapper objectMapper, Class<?> resourceClass)
    {
        Object instance = getDefaultRecord(validationContext, resourceClass, resourceClass);
        String json;
        try {
            json = objectMapper.writeValueAsString(instance);
        }
        catch (JsonProcessingException e) {
            throw new ValidatorException("Could not serialize Resource %s. Error: %s".formatted(resourceClass.getName(), e.getMessage()));
        }
        Object readInstance;
        try {
            readInstance = objectMapper.readValue(json, resourceClass);
        }
        catch (JsonProcessingException e) {
            throw new ValidatorException("Could not deserialize Resource %s. Error: %s".formatted(resourceClass.getName(), e.getMessage()));
        }
        if (!instance.equals(readInstance)) {
            throw new ValidatorException("Deserialized value does not match serialized value for Resource %s".formatted(resourceClass.getName()));
        }
    }

    private Object getDefaultRecord(ValidationContext validationContext, Type type, Class<?> clazz)
    {
        if (ApiPatch.class.isAssignableFrom(clazz)) {
            return new ApiPatch<>(ImmutableMap.of());
        }

        if (!clazz.isRecord()) {
            throw new ValidatorException("Resource %s is not a record".formatted(clazz.getName()));
        }

        validationContext.addValidatingResources(type);
        try {
            RecordComponent[] recordComponents = clazz.getRecordComponents();
            Object[] arguments = getDefaultValues(validationContext, recordComponents);
            Class<?>[] argumentTypes = getArgumentTypes(recordComponents);
            return clazz.getConstructor(argumentTypes).newInstance(arguments);
        }
        catch (ValidatorException e) {
            throw e;
        }
        catch (NoSuchMethodException e) {
            throw new ValidatorException("Constructor for record %s not found (check visibility, etc.). Message: %s".formatted(clazz.getName(), e.getMessage()));
        }
        catch (Exception e) {
            throw new ValidatorException("Could not instantiate record %s. Message: %s".formatted(clazz.getName(), e.getMessage()));
        }
        finally {
            validationContext.removeValidatingResources(type);
        }
    }

    private Class<?>[] getArgumentTypes(RecordComponent[] recordComponents)
    {
        Class<?>[] types = new Class[recordComponents.length];
        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            types[i] = recordComponent.getType();
        }
        return types;
    }

    private Object[] getDefaultValues(ValidationContext validationContext, RecordComponent[] recordComponents)
    {
        Object[] objects = new Object[recordComponents.length];
        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            objects[i] = getDefaultValue(validationContext, recordComponent.getType(), recordComponent.getGenericType());
        }
        return objects;
    }

    private Object getDefaultValue(ValidationContext validationContext, Class<?> clazz, Type type)
    {
        validationContext.addValidatingResources(type);
        try {
            Object value = valueCache.get(type);
            if (value == null) {    // don't use computeIfAbsent due to recursion
                value = internalGetDefaultValue(validationContext, clazz, type);
                valueCache.put(type, value);
            }
            return value;
        }
        finally {
            validationContext.removeValidatingResources(type);
        }
    }

    private Object internalGetDefaultValue(ValidationContext validationContext, Class<?> clazz, Type type)
    {
        if (boolean.class.isAssignableFrom(clazz) || Boolean.class.isAssignableFrom(clazz)) {
            return true;
        }
        if (int.class.isAssignableFrom(clazz) || Integer.class.isAssignableFrom(clazz)) {
            return 0;
        }
        if (long.class.isAssignableFrom(clazz) || Long.class.isAssignableFrom(clazz)) {
            return 0L;
        }
        if (double.class.isAssignableFrom(clazz) || Double.class.isAssignableFrom(clazz)) {
            return 0.0;
        }
        if (String.class.isAssignableFrom(clazz)) {
            return "empty"; // so that ErrorDetailType.Emtpy doesn't throw
        }
        if (Instant.class.isAssignableFrom(clazz)) {
            return Instant.now();
        }
        if (LocalDate.class.isAssignableFrom(clazz)) {
            return LocalDate.now();
        }
        if (BigDecimal.class.isAssignableFrom(clazz)) {
            return BigDecimal.ZERO;
        }
        if (Number.class.isAssignableFrom(clazz)) {
            return 0;
        }
        if (Enum.class.isAssignableFrom(clazz)) {
            return clazz.getEnumConstants()[0];
        }
        if (UUID.class.isAssignableFrom(clazz)) {
            return new UUID(0L, 0L);
        }
        if (List.class.isAssignableFrom(clazz) && (type instanceof ParameterizedType parameterizedType)) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];
            if (validationContext.isActiveValidatingResource(typeArgument)) {
                return ImmutableList.of();
            }
            return ImmutableList.of(getDefaultValue(validationContext, (Class<?>) typeArgument, typeArgument));
        }
        if (Collection.class.isAssignableFrom(clazz) && (type instanceof ParameterizedType parameterizedType)) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];
            if (validationContext.isActiveValidatingResource(typeArgument)) {
                return ImmutableSet.of();
            }
            return ImmutableSet.of(getDefaultValue(validationContext, (Class<?>) typeArgument, typeArgument));
        }
        if (Map.class.isAssignableFrom(clazz) && (type instanceof ParameterizedType parameterizedType)) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[1];
            return ImmutableMap.of("dummy", getDefaultValue(validationContext, (Class<?>) typeArgument, typeArgument));
        }
        if (Optional.class.isAssignableFrom(clazz) && (type instanceof ParameterizedType parameterizedType)) {
            Type typeArgument = parameterizedType.getActualTypeArguments()[0];
            Class<?> classArgument;
            if (typeArgument instanceof ParameterizedType parameterizedTypeArgument) {
                classArgument = (Class<?>) parameterizedTypeArgument.getRawType();
            }
            else {
                classArgument = (Class<?>) typeArgument;
            }
            if (validationContext.isActiveValidatingResource(typeArgument)) {
                return Optional.empty();
            }
            return Optional.of(getDefaultValue(validationContext, classArgument, typeArgument));
        }
        if (clazz.isInterface() && clazz.isSealed() && clazz.isAnnotationPresent(ApiPolyResource.class)) {
            return getDefaultValue(validationContext, clazz.getPermittedSubclasses()[0], clazz.getPermittedSubclasses()[0]);
        }
        if (clazz.isRecord()) {
            return getDefaultRecord(validationContext, type, clazz);
        }
        if (ApiId.class.isAssignableFrom(clazz.getSuperclass())) {
            try {
                validationContext.validateId(clazz);
                return clazz.getConstructor().newInstance();
            }
            catch (Exception e) {
                throw new ValidatorException("Could not create default value for ApiAbstractId %s".formatted(clazz.getName()));
            }
        }
        throw new ValidatorException("Could not create default value for %s".formatted(clazz.getName()));
    }
}
