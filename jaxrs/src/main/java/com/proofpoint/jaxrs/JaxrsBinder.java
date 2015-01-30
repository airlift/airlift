package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class JaxrsBinder
{
    private final Multibinder<Object> resourceBinder;
    private final Multibinder<Object> adminResourceBinder;
    private final Multibinder<JaxrsBinding> keyBinder;
    private final Binder binder;
    private final MapBinder<Class<?>, Supplier<?>> injectionProviderBinder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder cannot be null");
        this.resourceBinder = newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();
        this.adminResourceBinder = newSetBinder(binder, Object.class, AdminJaxrsResource.class).permitDuplicates();
        this.keyBinder = newSetBinder(binder, JaxrsBinding.class, JaxrsResource.class).permitDuplicates();
        injectionProviderBinder = newMapBinder(binder, new TypeLiteral<Class<?>>() {}, new TypeLiteral<Supplier<?>>() {}, JaxrsInjectionProvider.class);
    }

    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return new JaxrsBinder(binder);
    }

    public void bind(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bind(TypeLiteral<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        resourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bind(Key<?> targetKey)
    {
        binder.bind(targetKey).in(SINGLETON);
        resourceBinder.addBinding().to(targetKey).in(SINGLETON);
        registerJaxRsBinding(targetKey);
    }

    public void bindInstance(Object instance)
    {
        resourceBinder.addBinding().toInstance(instance);
    }

    public void bindAdmin(Class<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        adminResourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bindAdmin(TypeLiteral<?> implementation)
    {
        binder.bind(implementation).in(SINGLETON);
        adminResourceBinder.addBinding().to(implementation).in(SINGLETON);
        registerJaxRsBinding(Key.get(implementation));
    }

    public void bindAdmin(Key<?> targetKey)
    {
        binder.bind(targetKey).in(SINGLETON);
        adminResourceBinder.addBinding().to(targetKey).in(SINGLETON);
        registerJaxRsBinding(targetKey);
    }

    public void bindAdminInstance(Object instance)
    {
        adminResourceBinder.addBinding().toInstance(instance);
    }

    public void registerJaxRsBinding(Key<?> key)
    {
        keyBinder.addBinding().toInstance(new JaxrsBinding(key));
    }

    public <T> LinkedInjectionProviderBindingBuilder<T> bindInjectionProvider(Class<T> type) {
        return new LinkedInjectionProviderBindingBuilder<>(type, injectionProviderBinder);
    }
}
