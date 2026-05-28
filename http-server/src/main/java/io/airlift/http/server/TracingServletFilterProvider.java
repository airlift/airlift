package io.airlift.http.server;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.airlift.http.server.tracing.TracingServletFilter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.servlet.Filter;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.airlift.http.server.BinderUtils.qualifiedKey;
import static java.util.Objects.requireNonNull;

public class TracingServletFilterProvider
        implements com.google.inject.Provider<Filter>
{
    private final Optional<Class<? extends Annotation>> qualifier;
    private Injector injector;
    private OpenTelemetry telemetry;
    private Tracer tracer;

    public TracingServletFilterProvider(Optional<Class<? extends Annotation>> qualifier)
    {
        this.qualifier = requireNonNull(qualifier, "qualifier is null");
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = requireNonNull(injector, "injector is null");
    }

    @Inject
    public void setOpenTelemetry(OpenTelemetry openTelemetry)
    {
        this.telemetry = requireNonNull(openTelemetry, "openTelemetry is null");
    }

    @Inject
    public void setTracer(Tracer tracer)
    {
        this.tracer = requireNonNull(tracer, "tracer is null");
    }

    @Override
    public Filter get()
    {
        return new TracingServletFilter(telemetry, tracer, injector.getInstance(qualifiedKey(qualifier, HttpTracingConfig.class)));
    }
}
