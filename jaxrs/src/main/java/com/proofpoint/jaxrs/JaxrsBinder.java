package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class JaxrsBinder
{
    private final Binder binder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = checkNotNull(binder, "binder cannot be null");
    }

    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return new JaxrsBinder(binder);
    }

    public void bindResourceFilterFactory(Class<? extends ResourceFilterFactory> filterFactoryClass)
    {
        checkNotNull(filterFactoryClass, "filterFactoryClass is null");
        newSetBinder(binder, ResourceFilterFactory.class)
                .addBinding()
                .to(filterFactoryClass);
    }
}
