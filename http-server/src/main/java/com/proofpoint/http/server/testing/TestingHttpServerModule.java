package com.proofpoint.http.server.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class TestingHttpServerModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
    }
}
