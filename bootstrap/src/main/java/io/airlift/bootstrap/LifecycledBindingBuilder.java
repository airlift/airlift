package io.airlift.bootstrap;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;

import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates Provider bindings where objects created by the provider are automatically added to the lifecycle.
 *
 *
 * In a guice module, use
 * <pre>
 *   LifecycledBindingBuilder.lifecycleBinding(binder, Foo.class).toProvider(FooProvider.class).in(Scopes.SINGLETON);
 * </pre>
 *
 * All <tt>Foo</tt> objects created by the FooProvider will automatically added to the Lifecycle and their {@link PostConstruct}
 * and {@link PreDestroy} methods are called.
 */
public final class LifecycledBindingBuilder<T>
{
    private final Binder binder;
    private final Key<T> key;

    public static <Type> LifecycledBindingBuilder<Type> lifecycleBinding(Binder binder, Class<Type> clazz)
    {
        return new LifecycledBindingBuilder<Type>(binder, Key.get(checkNotNull(clazz, "clazz is null")));
    }

    public static <Type> LifecycledBindingBuilder<Type> lifecycleBinding(Binder binder, Key<Type> key)
    {
        return new LifecycledBindingBuilder<Type>(binder, key);
    }

    private LifecycledBindingBuilder(Binder binder, Key<T> key)
    {
        this.binder = checkNotNull(binder, "binder is null");
        this.key = checkNotNull(key, "key is null");
    }

    public ScopedBindingBuilder toProvider(Provider<? extends T> delegate)
    {
        return binder.bind(key).toProvider(new LifecyclingProvider<T>(delegate));
    }

    public ScopedBindingBuilder toProvider(Class<? extends Provider<? extends T>> providerType)
    {
        return binder.bind(key).toProvider(new LifecyclingProvider<T>(Key.get(providerType)));
    }

    public ScopedBindingBuilder toProvider(TypeLiteral<? extends Provider<? extends T>> providerType)
    {
        return binder.bind(key).toProvider(new LifecyclingProvider<T>(Key.get(providerType)));
    }

    public ScopedBindingBuilder toProvider(Key<? extends Provider<? extends T>> providerKey)
    {
        return binder.bind(key).toProvider(new LifecyclingProvider<T>(providerKey));
    }
}
