package io.airlift.jaxrs.tracing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.http.server.TheServlet;
import jakarta.servlet.Filter;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public final class JaxrsTracingModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        newSetBinder(binder, Filter.class, TheServlet.class).addBinding()
                .to(TracingServletFilter.class).in(Scopes.SINGLETON);

        jaxrsBinder(binder).bind(TracingDynamicFeature.class);
    }
}
