package io.airlift.jaxrs;

import com.google.inject.Inject;
import com.google.inject.Injector;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static io.airlift.jaxrs.JsonParsingFeature.MappingEnabled.DISABLED;
import static java.util.Objects.requireNonNull;

public record JsonParsingFeature(MappingEnabled enabled)
        implements Feature
{
    public enum MappingEnabled
    {
        ENABLED,
        DISABLED,
    }

    public JsonParsingFeature
    {
        requireNonNull(enabled, "config is null");
    }

    @Override
    public boolean configure(FeatureContext context)
    {
        if (enabled == DISABLED) {
            return false;
        }

        context.register(new JsonParsingExceptionMapper());
        return true;
    }

    public static class Provider
            implements com.google.inject.Provider<JsonParsingFeature>
    {
        private final Optional<Class<? extends Annotation>> qualifier;
        private Injector injector;

        public Provider(Optional<Class<? extends Annotation>> qualifier)
        {
            this.qualifier = requireNonNull(qualifier, "qualifier is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = requireNonNull(injector, "injector is null");
        }

        @Override
        public JsonParsingFeature get()
        {
            return new JsonParsingFeature(injector.getInstance(qualifiedKey(qualifier, MappingEnabled.class)));
        }
    }
}
