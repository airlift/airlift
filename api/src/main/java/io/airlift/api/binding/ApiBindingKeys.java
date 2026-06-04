package io.airlift.api.binding;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Optional;

public final class ApiBindingKeys
{
    private ApiBindingKeys() {}

    public static <T> Key<T> annotatedKey(Class<T> type, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return bindingAnnotation
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }

    public static <T> Key<T> annotatedKey(TypeLiteral<T> type, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return bindingAnnotation
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
