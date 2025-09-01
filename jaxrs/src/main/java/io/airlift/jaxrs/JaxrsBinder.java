package io.airlift.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.airlift.jaxrs.JsonParsingFeature.MappingEnabled;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static io.airlift.jaxrs.JsonParsingFeature.MappingEnabled.DISABLED;
import static java.util.Objects.requireNonNull;

public class JaxrsBinder
{
    private final Multibinder<Object> resourceBinder;
    private final Optional<Class<? extends Annotation>> qualifier;
    private final Binder binder;

    private JaxrsBinder(Binder binder, Optional<Class<? extends Annotation>> qualifier)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
        this.resourceBinder = newSetBinder(binder, qualifiedKey(qualifier, Object.class)).permitDuplicates();
    }

    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return jaxrsBinder(binder, Optional.empty());
    }

    public static JaxrsBinder jaxrsBinder(Binder binder, Class<? extends Annotation> qualifier)
    {
        return jaxrsBinder(binder, Optional.ofNullable(qualifier));
    }

    static JaxrsBinder jaxrsBinder(Binder binder, Optional<Class<? extends Annotation>> qualifier)
    {
        return new JaxrsBinder(binder, qualifier);
    }

    public <T> void bind(Class<T> implementation)
    {
        if (qualifier.isPresent()) {
            binder.bind(implementation)
                    .annotatedWith(qualifier.get())
                    .to(implementation)
                    .in(SINGLETON);
        }
        else {
            binder.bind(implementation).in(SINGLETON);
        }
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
    }

    public <T> void bind(Class<T> implementation, Provider<T> provider)
    {
        if (qualifier.isPresent()) {
            binder.bind(implementation)
                    .annotatedWith(qualifier.get())
                    .toProvider(provider)
                    .in(SINGLETON);
        }
        else {
            binder.bind(implementation).toProvider(provider).in(SINGLETON);
        }
        resourceBinder.addBinding().toProvider(provider).in(SINGLETON);
    }

    public <T> void bind(TypeLiteral<T> implementation)
    {
        newOptionalBinder(binder, implementation).setBinding().to(implementation);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
    }

    public JaxrsBinder disableJsonExceptionMapper()
    {
        newOptionalBinder(binder, qualifiedKey(qualifier, MappingEnabled.class)).setBinding().toInstance(DISABLED);
        return this;
    }
}
