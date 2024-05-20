package io.airlift.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.airlift.jaxrs.JaxrsModule.JerseyFactoryBinding;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.wadl.internal.WadlResource;
import org.glassfish.jersey.server.wadl.processor.OptionsMethodProcessor;
import org.glassfish.jersey.server.wadl.processor.WadlModelProcessor;

import java.util.Collection;
import java.util.function.Supplier;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class JaxrsBinder
{
    private final Multibinder<Object> resourceBinder;
    private final Multibinder<JerseyFactoryBinding> factoryBinder;

    private final Binder binder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
        this.factoryBinder = newSetBinder(binder, JerseyFactoryBinding.class);
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

    public <T> void bindRequestScoped(Class<T> requestScopeClass, Class<? extends Supplier<T>> scopedFactory)
    {
        factoryBinder.addBinding().toInstance(scopedFactoryBinding(requestScopeClass, scopedFactory));
    }

    public void bindInstance(Object instance)
    {
        resourceBinder.addBinding().toInstance(instance);
    }

    public static Collection<Class<?>> getBuiltinResources()
    {
        return ImmutableList.of(WadlResource.class, WadlModelProcessor.class, OptionsMethodProcessor.class);
    }

    private <T> JerseyFactoryBinding scopedFactoryBinding(Class<T> scopedClazz, Class<? extends Supplier<T>> scopedFactory)
    {
        return binder -> binder.bindFactory(scopedFactory)
                .to(scopedClazz)
                .proxy(false)
                .proxyForSameScope(false)
                .in(RequestScoped.class);
    }
}
