package io.airlift.api.binding;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import java.lang.annotation.Annotation;
import java.util.Optional;

public final class ApiBinders
{
    private ApiBinders() {}

    public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return bindingAnnotation
                .map(annotation -> MapBinder.newMapBinder(binder, keyType, valueType, annotation))
                .orElseGet(() -> MapBinder.newMapBinder(binder, keyType, valueType));
    }

    public static <T> Multibinder<T> newSetBinder(Binder binder, TypeLiteral<T> type, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return bindingAnnotation
                .map(annotation -> Multibinder.newSetBinder(binder, type, annotation))
                .orElseGet(() -> Multibinder.newSetBinder(binder, type));
    }
}
