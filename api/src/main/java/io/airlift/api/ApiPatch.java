package io.airlift.api;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import io.airlift.api.validation.ValidatorException;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.api.internals.Generics.validateMap;
import static io.airlift.api.responses.ApiException.badRequest;
import static io.airlift.api.validation.ValidationContext.isForcedReadOnly;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

public record ApiPatch<RESOURCE>(Map<String, Function<Type, Object>> fields)
{
    public ApiPatch
    {
        fields = ImmutableMap.copyOf(fields);
    }

    public RESOURCE apply(RESOURCE originalValue)
    {
        return apply(originalValue, fields.keySet(), (name, type) -> fields.get(name).apply(type));
    }

    @SuppressWarnings("unchecked")
    public static <T> T apply(T originalValue, Collection<String> fields, BiFunction<String, Type, Object> supplier)
    {
        checkArgument(requireNonNull(originalValue, "originalValue is null").getClass().isRecord(), "originalValue instance must be a record");
        requireNonNull(supplier, "supplier is null");

        Map<String, ? extends Set<String>> secondLevels = buildSecondLevels(fields);

        return (T) buildRecord(fields, secondLevels, originalValue, supplier);
    }

    public static Map.Entry<String, String> buildSecondLevel(String field)
    {
        List<String> parts = Splitter.on('.').splitToList(field);
        return switch (parts.size()) {
            case 1 -> Map.entry(field, "");
            case 2 -> Map.entry(parts.get(0), parts.get(1));
            default -> throw new ValidatorException("Invalid field: %s".formatted(field));
        };
    }

    private static Object buildRecord(Collection<String> fields, Map<String, ? extends Set<String>> secondLevels, Object originalValue, BiFunction<String, Type, Object> supplier)
    {
        RecordComponent[] recordComponents = originalValue.getClass().getRecordComponents();

        Object[] arguments = new Object[recordComponents.length];
        Class<?>[] argumentTypes = new Class<?>[recordComponents.length];

        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];

            argumentTypes[i] = recordComponent.getType();

            if (recordComponent.isAnnotationPresent(ApiUnwrapped.class)) {
                try {
                    Object unwrappedValue = recordComponent.getAccessor().invoke(originalValue);
                    arguments[i] = buildRecord(fields, secondLevels, unwrappedValue, supplier);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            else if (fields.contains(recordComponent.getName())) {
                validateNotReadOnly(recordComponent);
                arguments[i] = supplier.apply(recordComponent.getName(), recordComponent.getGenericType());
            }
            else {
                try {
                    Object argumentValue = recordComponent.getAccessor().invoke(originalValue);
                    Set<String> secondLevelFields = secondLevels.get(recordComponent.getName());
                    if (secondLevelFields != null) {
                        Object appliedValue = supplier.apply(recordComponent.getName(), recordComponent.getGenericType());
                        arguments[i] = getSecondLevel(recordComponent, argumentValue, appliedValue, secondLevelFields);
                    }
                    else {
                        arguments[i] = argumentValue;
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        try {
            return originalValue.getClass().getConstructor(argumentTypes).newInstance(arguments);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getSecondLevel(RecordComponent recordComponent, Object argumentValue, Object appliedValue, Set<String> secondLevelFields)
    {
        if ((argumentValue instanceof Map<?, ?> argumentMap) && (appliedValue instanceof Map<?, ?> appliedMap)) {
            validateMap(recordComponent.getGenericType());

            Map<String, String> copy = new HashMap<>((Map<String, String>) argumentMap);
            secondLevelFields.forEach(field -> {
                if (appliedMap.containsKey(field)) {
                    copy.put(field, (String) appliedMap.get(field));
                }
            });
            return copy;
        }

        throw new ValidatorException("Two level field patching is only supported for Maps");
    }

    private static Map<String, ? extends Set<String>> buildSecondLevels(Collection<String> fields)
    {
        return fields.stream()
                .filter(field -> field.contains("."))
                .map(ApiPatch::buildSecondLevel)
                .collect(groupingBy(Map.Entry::getKey, HashMap::new, Collectors.mapping(Map.Entry::getValue, toImmutableSet())));
    }

    private static void validateNotReadOnly(RecordComponent recordComponent)
    {
        if ((recordComponent.getAnnotation(ApiReadOnly.class) != null) || isForcedReadOnly(recordComponent.getType())) {
            throw badRequest("Attempt to update a read-only field: " + recordComponent.getName());
        }
    }
}
