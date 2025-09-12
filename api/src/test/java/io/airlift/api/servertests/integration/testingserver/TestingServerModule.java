package io.airlift.api.servertests.integration.testingserver;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.api.servertests.integration.testingserver.internal.InternalController;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class TestingServerModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.install(new TestingApiModule());

        jaxrsBinder(binder).bind(TestingOpenApiResource.class);

        binder.bind(InternalController.class).in(Scopes.SINGLETON);
    }
}
