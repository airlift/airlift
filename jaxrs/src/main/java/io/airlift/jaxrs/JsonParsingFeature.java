package io.airlift.jaxrs;

import static io.airlift.jaxrs.JsonParsingFeature.MappingEnabled.DISABLED;
import static java.util.Objects.requireNonNull;

import com.google.inject.Inject;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

public record JsonParsingFeature(MappingEnabled enabled) implements Feature {
    public enum MappingEnabled {
        ENABLED,
        DISABLED,
    }

    @Inject
    public JsonParsingFeature {
        requireNonNull(enabled, "config is null");
    }

    @Override
    public boolean configure(FeatureContext context) {
        if (enabled == DISABLED) {
            return false;
        }

        context.register(new JsonParsingExceptionMapper());
        return true;
    }
}
