package io.airlift.jaxrs;

import com.google.inject.Inject;
import com.google.inject.Injector;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static io.airlift.jaxrs.YamlParsingFeature.MappingEnabled.DISABLED;
import static java.util.Objects.requireNonNull;

public record YamlParsingFeature(MappingEnabled enabled)
        implements Feature
{
    public enum MappingEnabled
    {
        ENABLED,
        DISABLED,
    }

    public YamlParsingFeature
    {
        requireNonNull(enabled, "enabled is null");
    }

    @Override
    public boolean configure(FeatureContext context)
    {
        if (enabled == DISABLED) {
            return false;
        }

        context.register(new YamlParsingExceptionMapper());
        return true;
    }

    public static class Provider
            implements com.google.inject.Provider<YamlParsingFeature>
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
        public YamlParsingFeature get()
        {
            return new YamlParsingFeature(injector.getInstance(qualifiedKey(qualifier, MappingEnabled.class)));
        }
    }
}
