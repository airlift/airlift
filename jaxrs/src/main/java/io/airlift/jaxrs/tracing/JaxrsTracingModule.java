package io.airlift.jaxrs.tracing;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

import com.google.inject.Binder;
import com.google.inject.Module;

public final class JaxrsTracingModule implements Module {
    @Override
    public void configure(Binder binder) {
        jaxrsBinder(binder).bind(TracingDynamicFeature.class);
    }
}
