package io.airlift.api.binding;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;

import java.lang.annotation.Annotation;
import java.util.Optional;

final class ApiMapBinders
{
    private ApiMapBinders() {}

    public static <K, V> MapBinder<K, V> newMapBinder(Binder binder, TypeLiteral<K> keyType, TypeLiteral<V> valueType, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        return bindingAnnotation
                .map(annotation -> MapBinder.newMapBinder(binder, keyType, valueType, annotation))
                .orElseGet(() -> MapBinder.newMapBinder(binder, keyType, valueType));
    }
}
