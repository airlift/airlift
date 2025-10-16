package io.airlift.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import org.glassfish.jersey.server.ResourceConfig;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;
import static org.glassfish.jersey.server.ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR;

public class JaxrsResourceConfigProvider
        implements Provider<ResourceConfig>
{
    private final Optional<Class<? extends Annotation>> qualifier;
    private Injector injector;

    public JaxrsResourceConfigProvider(Optional<Class<? extends Annotation>> qualifier)
    {
        this.qualifier = requireNonNull(qualifier, "resourcesQualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Override
    public ResourceConfig get()
    {
        Set<Object> singletons = injector.getInstance(qualifiedKey(qualifier, new TypeLiteral<>() {}));
        return new JaxrsResourceConfig(singletons)
                .setProperties(ImmutableMap.of(RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true"));
    }
}
