package io.airlift.api.internals;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class JacksonEnumValueResolver
        implements ApiEnumValueResolver
{
    public JacksonEnumValueResolver() {}

    @Override
    public List<String> values(Class<?> enumClass)
    {
        validateEnumClass(enumClass);

        ImmutableList.Builder<String> values = ImmutableList.builder();
        Set<String> seenValues = new HashSet<>();
        for (Object constant : enumClass.getEnumConstants()) {
            String value = value((Enum<?>) constant);
            if (!seenValues.add(value)) {
                throw new ValidatorException("Duplicate enum wire value \"%s\" for enum %s".formatted(value, enumClass.getName()));
            }
            values.add(value);
        }
        return values.build();
    }

    @Override
    public String value(Enum<?> value)
    {
        requireNonNull(value, "value is null");
        Optional<Method> jsonValueMethod = jsonValueMethod(value.getDeclaringClass());
        if (jsonValueMethod.isEmpty()) {
            return value.toString();
        }

        Method method = jsonValueMethod.orElseThrow();
        if (method.getName().equals("toString")) {
            return value.toString();
        }

        Object resolvedValue;
        try {
            if (!method.canAccess(value)) {
                throw new ValidatorException("@JsonValue method %s is not accessible on enum %s".formatted(method.getName(), value.getDeclaringClass().getName()));
            }
            resolvedValue = method.invoke(value);
        }
        catch (IllegalAccessException e) {
            throw new ValidatorException("@JsonValue method %s is not accessible on enum %s".formatted(method.getName(), value.getDeclaringClass().getName()), e);
        }
        catch (InvocationTargetException e) {
            throw new ValidatorException("@JsonValue method %s threw an exception for enum %s".formatted(method.getName(), value.getDeclaringClass().getName()), e);
        }

        if (resolvedValue == null) {
            throw new ValidatorException("@JsonValue method %s on enum %s returned null".formatted(method.getName(), value.getDeclaringClass().getName()));
        }
        if (!(resolvedValue instanceof String stringValue)) {
            throw new ValidatorException("@JsonValue method %s on enum %s must return a String".formatted(method.getName(), value.getDeclaringClass().getName()));
        }
        return stringValue;
    }

    private static Optional<Method> jsonValueMethod(Class<?> enumClass)
    {
        validateEnumClass(enumClass);

        Method[] methods = Stream.of(enumClass.getDeclaredMethods())
                .filter(method -> Optional.ofNullable(method.getAnnotation(JsonValue.class))
                        .map(JsonValue::value)
                        .orElse(false))
                .toArray(Method[]::new);

        if (methods.length > 1) {
            throw new ValidatorException("Multiple @JsonValue methods found on enum %s".formatted(enumClass.getName()));
        }
        if (methods.length == 0) {
            return Optional.empty();
        }

        Method method = methods[0];
        if (method.getParameterCount() != 0) {
            throw new ValidatorException("@JsonValue method %s on enum %s must not have parameters".formatted(method.getName(), enumClass.getName()));
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new ValidatorException("@JsonValue method %s is not an instance method on enum %s".formatted(method.getName(), enumClass.getName()));
        }
        return Optional.of(method);
    }

    private static void validateEnumClass(Class<?> enumClass)
    {
        requireNonNull(enumClass, "enumClass is null");
        if (!enumClass.isEnum()) {
            throw new ValidatorException("%s is not an enum".formatted(enumClass.getName()));
        }
        long jsonValueFields = Stream.of(enumClass.getDeclaredFields())
                .filter(field -> Optional.ofNullable(field.getAnnotation(JsonValue.class))
                        .map(JsonValue::value)
                        .orElse(false))
                .count();
        if (jsonValueFields > 0) {
            // Jackson supports field-based @JsonValue, but API Builder only resolves method-based enum values for now.
            throw new ValidatorException("@JsonValue fields are not supported on enum %s".formatted(enumClass.getName()));
        }
    }
}
