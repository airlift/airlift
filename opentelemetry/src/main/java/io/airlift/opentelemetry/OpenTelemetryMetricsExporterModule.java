package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.metrics.MetricsModule;
import io.opentelemetry.sdk.metrics.export.MetricProducer;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class OpenTelemetryMetricsExporterModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.install(new MetricsModule());
        binder.bind(OpenTelemetryMetricDataConverter.class).in(SINGLETON);
        newSetBinder(binder, MetricProducer.class).addBinding()
                .to(OpenTelemetryMetricProducer.class)
                .in(SINGLETON);
    }
}
