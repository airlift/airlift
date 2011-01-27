package com.proofpoint.platform.skeleton;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class MainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(StatusResource.class).in(Scopes.SINGLETON);
    }
}
