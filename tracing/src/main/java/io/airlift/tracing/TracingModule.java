package io.airlift.tracing;

import com.google.inject.Binder;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.opentelemetry.api.OpenTelemetry;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class TracingModule
        extends AbstractConfigurationAwareModule
{
    private final String serviceName;
    private final String serviceVersion;

    public TracingModule(String serviceName, String serviceVersion)
    {
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
        this.serviceVersion = requireNonNull(serviceVersion, "serviceVersion is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        if (buildConfigObject(TracingEnabledConfig.class).isEnabled()) {
            install(new OpenTelemetryModule(serviceName, serviceVersion, Optional.empty()));
        }
        else {
            binder.bind(OpenTelemetry.class).toInstance(OpenTelemetry.noop());
        }

        install(new TracingSupportModule(serviceName));
    }
}
