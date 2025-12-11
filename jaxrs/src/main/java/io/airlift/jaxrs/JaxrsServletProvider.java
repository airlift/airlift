package io.airlift.jaxrs;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import jakarta.servlet.Servlet;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;

public class JaxrsServletProvider
        implements Provider<Servlet>
{
    private final Optional<Class<? extends Annotation>> qualifier;
    private Injector injector;

    public JaxrsServletProvider(Optional<Class<? extends Annotation>> qualifier)
    {
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public Servlet get()
    {
        return new ServletContainer(injector.getInstance(qualifiedKey(qualifier, ResourceConfig.class)));
    }
}
