package io.airlift.http.server;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Optional;

class BinderUtils
{
    private BinderUtils() {}

    static <T> Key<T> qualifiedKey(Optional<Class<? extends Annotation>> qualifier, Class<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }

    static <T> Key<T> qualifiedKey(Optional<Class<? extends Annotation>> qualifier, TypeLiteral<T> type)
    {
        return qualifier
                .map(annotation -> Key.get(type, annotation))
                .orElseGet(() -> Key.get(type));
    }
}
