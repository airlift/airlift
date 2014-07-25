package com.proofpoint.jaxrs;

import com.google.inject.Binder;

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
}
