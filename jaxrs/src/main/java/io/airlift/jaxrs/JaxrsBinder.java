package io.airlift.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class JaxrsBinder
{
    private final Multibinder<Object> resourceBinder;
    private final Binder binder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.resourceBinder = newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
    }

    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return new JaxrsBinder(binder);
    }

    public void bind(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
    }

    public void bind(TypeLiteral<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
    }

    public void bind(Key<?> targetKey)
    {
        binder.bind(targetKey).in(SINGLETON);
        resourceBinder.addBinding().to(targetKey).in(SINGLETON);
    }

    public void bindInstance(Object instance)
    {
        resourceBinder.addBinding().toInstance(instance);
    }
}
