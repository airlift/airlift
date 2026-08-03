package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.bootstrap.LoggingBootstrap;
import io.airlift.bootstrap.LoggingBootstrapContext;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Optional;
import java.util.function.BiFunction;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class OpenTelemetryLoggingModule
        implements LoggingBootstrap, Module
{
    private final String serviceName;
    private final String serviceVersion;
    private final BiFunction<OpenTelemetryExporterConfig, MeterProvider, LogRecordProcessor> logRecordProcessorFactory;

    private boolean initialized;
    private boolean enabled;
    private Optional<SdkLoggerProvider> loggerProvider = Optional.empty();
    private Optional<OpenTelemetryLogHandler> logHandler = Optional.empty();

    public OpenTelemetryLoggingModule(String serviceName, String serviceVersion)
    {
        this(serviceName, serviceVersion, OpenTelemetryExporterModule::createLogRecordProcessor);
    }

    OpenTelemetryLoggingModule(
            String serviceName,
            String serviceVersion,
            BiFunction<OpenTelemetryExporterConfig, MeterProvider, LogRecordProcessor> logRecordProcessorFactory)
    {
        this.serviceName = requireNonNull(serviceName, "serviceName is null");
        this.serviceVersion = requireNonNull(serviceVersion, "serviceVersion is null");
        this.logRecordProcessorFactory = requireNonNull(logRecordProcessorFactory, "logRecordProcessorFactory is null");
    }

    @Override
    public synchronized void initializeLogging(LoggingBootstrapContext context)
    {
        requireNonNull(context, "context is null");

        ConfigurationFactory configurationFactory = context.getConfigurationFactory();
        OpenTelemetryLoggingConfig loggingConfig = configurationFactory.build(OpenTelemetryLoggingConfig.class);
        initialized = true;
        enabled = loggingConfig.isEnabled();
        if (!loggingConfig.isEnabled()) {
            return;
        }

        OpenTelemetryExporterConfig exporterConfig = configurationFactory.build(OpenTelemetryExporterConfig.class);
        NodeConfig nodeConfig = configurationFactory.build(NodeConfig.class);
        if (nodeConfig.getNodeId() == null) {
            throw new IllegalStateException("log.otlp.enabled=true requires node.id to be configured");
        }
        NodeInfo nodeInfo = new NodeInfo(nodeConfig);
        Resource resource = OpenTelemetryModule.createResource(serviceName, serviceVersion, nodeInfo);
        LogRecordProcessor logRecordProcessor = logRecordProcessorFactory.apply(exporterConfig, MeterProvider.noop());

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(logRecordProcessor)
                .build();
        OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, context);

        context.addRootHandler(handler);
        this.loggerProvider = Optional.of(loggerProvider);
        this.logHandler = Optional.of(handler);
    }

    @Override
    public synchronized void configure(Binder binder)
    {
        configBinder(binder).bindConfig(OpenTelemetryLoggingConfig.class);
        if (!initialized) {
            throw new IllegalStateException("OpenTelemetryLoggingModule requires Bootstrap logging initialization");
        }
        if (enabled && loggerProvider.isEmpty()) {
            throw new IllegalStateException("log.otlp.enabled=true requires OpenTelemetryLoggingModule to initialize logging");
        }
        // Do not register this provider for lifecycle cleanup. Logging should remain available
        // while the application shuts down; applications that need explicit cleanup can fetch
        // the SdkLoggerProvider binding and close it directly.
        loggerProvider.ifPresent(provider -> newOptionalBinder(binder, SdkLoggerProvider.class)
                .setBinding()
                .toInstance(provider));
        logHandler.ifPresent(handler -> {
            newOptionalBinder(binder, OpenTelemetryLogHandler.class)
                    .setBinding()
                    .toInstance(handler);
            newExporter(binder).export(OpenTelemetryLogHandler.class).withGeneratedName();
        });
    }
}
