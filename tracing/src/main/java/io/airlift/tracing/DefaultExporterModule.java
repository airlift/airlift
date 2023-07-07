package io.airlift.tracing;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

public class DefaultExporterModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(OpenTelemetryConfig.class);
        binder.bind(SpanProcessor.class).toProvider(OltpGrpcSpanExporterProvider.class);
    }

    private static class OltpGrpcSpanExporterProvider
            implements Provider<SpanProcessor>
    {
        private final String endpoint;

        @Inject
        public OltpGrpcSpanExporterProvider(OpenTelemetryConfig config)
        {
            this.endpoint = requireNonNull(config, "config is null").getEndpoint();
        }

        @Override
        public SpanProcessor get()
        {
            SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .build();

            return BatchSpanProcessor.builder(spanExporter).build();
        }
    }
}
