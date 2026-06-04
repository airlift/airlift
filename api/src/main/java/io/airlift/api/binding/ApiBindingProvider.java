package io.airlift.api.binding;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.api.binding.ApiBindingKeys.annotatedKey;
import static java.util.Objects.requireNonNull;

final class ApiBindingProvider
{
    private final Binder binder;
    private final Optional<Class<? extends Annotation>> bindingAnnotation;

    public ApiBindingProvider(Binder binder, Optional<Class<? extends Annotation>> bindingAnnotation)
    {
        this.binder = requireNonNull(binder, "binder is null");
        this.bindingAnnotation = requireNonNull(bindingAnnotation, "bindingAnnotation is null");
    }

    <T> Provider<T> get(Class<T> type)
    {
        return binder.getProvider(annotatedKey(type, bindingAnnotation));
    }

    <T> Provider<T> get(TypeLiteral<T> type)
    {
        return binder.getProvider(annotatedKey(type, bindingAnnotation));
    }

    <T> Provider<T> getUnqualified(Class<T> type)
    {
        return binder.getProvider(type);
    }
}
