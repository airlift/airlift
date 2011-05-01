package com.proofpoint.experimental.event.client;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class NullEventModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(NullEventClient.class).in(Scopes.SINGLETON);
    }
}
