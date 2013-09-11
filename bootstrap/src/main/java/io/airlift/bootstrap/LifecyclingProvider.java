package io.airlift.bootstrap;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ProviderWithDependencies;

import javax.inject.Provider;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Wraps an existing provider and adds all objects from that provider to the lifecycle.
 */
class LifecyclingProvider<T>
    implements ProviderWithDependencies<T>
{
    private final Key<? extends Provider<? extends T>> key;
    private final Set<Dependency<?>> dependencies;

    private Provider<? extends T> delegate;
    private LifeCycleManager lifeCycleManager;

    LifecyclingProvider(Provider<? extends T> delegate)
    {
        this.delegate = checkNotNull(delegate, "delegate is null");
        this.key = null;

        Set<InjectionPoint> injectionPoints = InjectionPoint.forInstanceMethodsAndFields(delegate.getClass());
        ImmutableSet.Builder<Dependency<?>> builder = ImmutableSet.builder();
        for (InjectionPoint ip : injectionPoints) {
            builder.addAll(ip.getDependencies());
        }
        this.dependencies = builder.build();

    }

    LifecyclingProvider(Key<? extends Provider<? extends T>> key)
    {
        this.key = checkNotNull(key, "key is null");

        this.delegate = null;
        this.dependencies = ImmutableSet.of();
    }

    @Inject
    void setInjector(Injector injector)
    {
        checkNotNull(injector, "injector is null");
        this.lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        if (key != null) {
            this.delegate = injector.getInstance(key);
        }
        else {
            injector.injectMembers(delegate);
        }
    }

    @Override
    public T get()
    {
        checkState(delegate != null, "delegate is null");

        T value = delegate.get();

        try {
            lifeCycleManager.addInstance(value);
        }
        catch (Exception e) {
            throw new ProvisionException("While adding to lifecycle manager", e);
        }
        return value;
    }

    @Override
    public Set<Dependency<?>> getDependencies()
    {
        return dependencies;
    }
}
