package io.airlift.opentelemetry;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.bootstrap.LoggingBootstrap;
import io.airlift.bootstrap.LoggingBootstrapContext;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Handler;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;
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
    private Optional<LogRecordProcessor> logRecordProcessor = Optional.empty();
    private Optional<MetricProducer> logMetricProducer = Optional.empty();
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

        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        synchronized (rootLogger) {
            Optional<OpenTelemetryLogHandler> existingHandler = findOpenTelemetryLogHandler(rootLogger);
            if (existingHandler.isPresent()) {
                useLogHandler(existingHandler.orElseThrow());
                enabled = true;
                return;
            }

            if (!enabled) {
                return;
            }

            OpenTelemetryExporterConfig exporterConfig = configurationFactory.build(OpenTelemetryExporterConfig.class);
            NodeConfig nodeConfig = configurationFactory.build(NodeConfig.class);
            if (nodeConfig.getNodeId() == null) {
                throw new IllegalStateException("log.otlp.enabled=true requires node.id to be configured");
            }
            NodeInfo nodeInfo = new NodeInfo(nodeConfig);
            Resource resource = OpenTelemetryModule.createResource(serviceName, serviceVersion, nodeInfo);

            // Logging starts before Guice creates the application meter provider. This provider only
            // aggregates the log processor metrics; LogProcessorMetrics exposes them to the Guice provider.
            LogProcessorMetrics logProcessorMetrics = new LogProcessorMetrics();
            SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(logProcessorMetrics)
                    .build();
            LogRecordProcessor logRecordProcessor = logRecordProcessorFactory.apply(exporterConfig, meterProvider);

            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(resource, logRecordProcessor, logProcessorMetrics, context);
            context.addRootHandler(handler);
            useLogHandler(handler);
        }
    }

    private void useLogHandler(OpenTelemetryLogHandler handler)
    {
        logRecordProcessor = Optional.of(handler.getInitialLogRecordProcessor());
        logMetricProducer = Optional.of(handler.getMetricProducer());
        loggerProvider = Optional.of(handler.getLoggerProvider());
        logHandler = Optional.of(handler);
    }

    private static Optional<OpenTelemetryLogHandler> findOpenTelemetryLogHandler(java.util.logging.Logger rootLogger)
    {
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof OpenTelemetryLogHandler openTelemetryLogHandler) {
                return Optional.of(openTelemetryLogHandler);
            }
        }
        return Optional.empty();
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
        logRecordProcessor.ifPresent(processor -> newOptionalBinder(binder, LogRecordProcessor.class)
                .setBinding()
                .toInstance(processor));
        logMetricProducer.ifPresent(producer -> newSetBinder(binder, MetricProducer.class)
                .addBinding()
                .toInstance(producer));
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

    private static final class LogProcessorMetrics
            implements MetricReader, MetricProducer
    {
        private volatile CollectionRegistration collectionRegistration = CollectionRegistration.noop();

        @Override
        public void register(CollectionRegistration collectionRegistration)
        {
            this.collectionRegistration = requireNonNull(collectionRegistration, "collectionRegistration is null");
        }

        @Override
        public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType)
        {
            return CUMULATIVE;
        }

        @Override
        public CompletableResultCode forceFlush()
        {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown()
        {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public synchronized Collection<MetricData> produce(Resource resource)
        {
            return collectionRegistration.collectAllMetrics();
        }
    }
}
