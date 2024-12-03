package io.airlift.jaxrs.tracing;

import com.google.inject.Binder;
import com.google.inject.Module;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public final class JaxrsTracingModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        jaxrsBinder(binder).bind(TracingDynamicFeature.class);
    }
}
