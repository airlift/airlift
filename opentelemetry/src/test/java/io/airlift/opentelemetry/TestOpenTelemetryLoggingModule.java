package io.airlift.opentelemetry;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.ProvisionListener.ProvisionInvocation;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.log.Logger;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.logging.Handler;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.file.Files.writeString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(OrderAnnotation.class)
public class TestOpenTelemetryLoggingModule
{
    @TempDir
    private Path tempDir;

    @Test
    @Order(1)
    public void testLogHandlerIsBoundExportedAndReused()
            throws IOException
    {
        Path annotationFile = tempDir.resolve("annotations.properties");
        writeString(annotationFile, "cluster=test-cluster\n");

        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        InMemoryLogRecordExporter customExporter = InMemoryLogRecordExporter.create();
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        AtomicReference<LogRecordProcessor> logRecordProcessor = new AtomicReference<>();
        AtomicBoolean defaultProcessorCreated = new AtomicBoolean();
        Set<SdkLoggerProvider> loggerProviders = Collections.newSetFromMap(new IdentityHashMap<>());
        OpenTelemetryLoggingModule loggingModule = new OpenTelemetryLoggingModule(
                "test-service",
                "test-version",
                (_, meterProvider) -> {
                    LogRecordProcessor processor = BatchLogRecordProcessor.builder(exporter)
                            .setMeterProvider(meterProvider)
                            .setInternalTelemetryVersion(InternalTelemetryVersion.LATEST)
                            .build();
                    logRecordProcessor.set(processor);
                    return processor;
                });

        Injector injector = new Bootstrap(
                new TestingNodeModule("testenv"),
                new TestingMBeanModule(),
                new OpenTelemetryModule("test-service", "test-version"),
                binder -> newSetBinder(binder, MetricReader.class)
                        .addBinding()
                        .toInstance(metricReader),
                binder -> binder.bindListener(Matchers.any(), new ProvisionListener()
                {
                    @Override
                    public <T> void onProvision(ProvisionInvocation<T> provision)
                    {
                        T value = provision.provision();
                        if (value instanceof SdkLoggerProvider loggerProvider) {
                            loggerProviders.add(loggerProvider);
                        }
                    }
                }),
                binder -> newOptionalBinder(binder, LogRecordProcessor.class)
                        .setDefault()
                        .toProvider(() -> {
                            defaultProcessorCreated.set(true);
                            return SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create());
                        }),
                binder -> newSetBinder(binder, LogRecordProcessor.class)
                        .addBinding()
                        .toInstance(SimpleLogRecordProcessor.create(customExporter)),
                loggingModule)
                .quiet()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .setOptionalConfigurationProperty("node.id", "testnode")
                .setOptionalConfigurationProperty("node.annotation-file", annotationFile.toString())
                .initialize();

        try {
            assertThat(defaultProcessorCreated).isFalse();
            assertThat(loggerProviders).hasSize(1);
            assertThat(injector.getInstance(Key.get(new TypeLiteral<Optional<LogRecordProcessor>>() {})))
                    .contains(logRecordProcessor.get());

            Logger.get("test.airlift.otlp").info("adopted provider log");
            Optional<OpenTelemetryLogHandler> logHandler = injector.getInstance(Key.get(new TypeLiteral<Optional<OpenTelemetryLogHandler>>() {}));
            assertThat(logHandler).isPresent();
            assertThat(injector.getInstance(SdkLoggerProvider.class)).isSameAs(logHandler.orElseThrow().getLoggerProvider());
            assertHandlerIsExported(injector.getInstance(MBeanExporter.class), logHandler.orElseThrow());

            injector.getInstance(OpenTelemetry.class)
                    .getLogsBridge()
                    .get("test.sdk.otlp")
                    .logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setBody("sdk provider log")
                    .emit();

            assertThat(logHandler.orElseThrow().getLoggerProvider().forceFlush().join(10, SECONDS).isSuccess()).isTrue();

            assertThat(exporter.getFinishedLogRecordItems())
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.airlift.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("adopted provider log");
                        assertThat(record.getAttributes().get(stringKey("cluster"))).isEqualTo("test-cluster");
                    })
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.sdk.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("sdk provider log");
                    });
            assertThat(customExporter.getFinishedLogRecordItems())
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.airlift.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("adopted provider log");
                    })
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.sdk.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("sdk provider log");
                    });
            assertThat(metricReader.collectAllMetrics())
                    .extracting(metric -> metric.getName())
                    .contains(
                            "otel.sdk.processor.log.processed",
                            "otel.sdk.processor.log.queue.capacity",
                            "otel.sdk.processor.log.queue.size");
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
            removeOpenTelemetryLogHandlers();
            injector.getInstance(SdkLoggerProvider.class).shutdown().join(10, SECONDS);
        }
    }

    @Test
    @Order(2)
    public void testMultipleBootstrapsReuseLogHandler()
    {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        InMemoryLogRecordExporter sharedCustomExporter = InMemoryLogRecordExporter.create();
        InMemoryLogRecordExporter secondCustomExporter = InMemoryLogRecordExporter.create();
        LogRecordProcessor sharedCustomProcessor = SimpleLogRecordProcessor.create(sharedCustomExporter);
        AtomicInteger logRecordProcessorsCreated = new AtomicInteger();
        BiFunction<OpenTelemetryExporterConfig, MeterProvider, LogRecordProcessor> processorFactory = (_, _) -> {
            logRecordProcessorsCreated.incrementAndGet();
            return SimpleLogRecordProcessor.create(exporter);
        };

        Injector firstInjector = new Bootstrap(
                new TestingNodeModule("testenv"),
                new OpenTelemetryModule("test-service", "test-version"),
                binder -> newSetBinder(binder, LogRecordProcessor.class)
                        .addBinding()
                        .toInstance(sharedCustomProcessor),
                new OpenTelemetryLoggingModule("test-service", "test-version", processorFactory))
                .quiet()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .setOptionalConfigurationProperty("node.id", "testnode")
                .initialize();
        Injector secondInjector = new Bootstrap(
                new TestingNodeModule("testenv"),
                new OpenTelemetryModule("test-service", "test-version"),
                binder -> {
                    newSetBinder(binder, LogRecordProcessor.class)
                            .addBinding()
                            .toInstance(sharedCustomProcessor);
                    newSetBinder(binder, LogRecordProcessor.class)
                            .addBinding()
                            .toInstance(SimpleLogRecordProcessor.create(secondCustomExporter));
                },
                new OpenTelemetryLoggingModule("test-service", "test-version", processorFactory))
                .quiet()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .setOptionalConfigurationProperty("node.id", "testnode")
                .initialize();

        SdkLoggerProvider loggerProvider = firstInjector.getInstance(SdkLoggerProvider.class);
        try {
            OpenTelemetryLogHandler firstHandler = firstInjector.getInstance(Key.get(new TypeLiteral<Optional<OpenTelemetryLogHandler>>() {})).orElseThrow();
            OpenTelemetryLogHandler secondHandler = secondInjector.getInstance(Key.get(new TypeLiteral<Optional<OpenTelemetryLogHandler>>() {})).orElseThrow();

            assertThat(logRecordProcessorsCreated).hasValue(1);
            assertThat(secondHandler).isSameAs(firstHandler);
            assertThat(secondInjector.getInstance(SdkLoggerProvider.class)).isSameAs(loggerProvider);
            assertThat(secondInjector.getInstance(Key.get(new TypeLiteral<Optional<LogRecordProcessor>>() {})))
                    .isEqualTo(firstInjector.getInstance(Key.get(new TypeLiteral<Optional<LogRecordProcessor>>() {})));
            assertThat(secondInjector.getInstance(Key.get(new TypeLiteral<Set<MetricProducer>>() {})))
                    .containsExactlyElementsOf(firstInjector.getInstance(Key.get(new TypeLiteral<Set<MetricProducer>>() {})));
            assertThat(java.util.logging.Logger.getLogger("").getHandlers())
                    .filteredOn(OpenTelemetryLogHandler.class::isInstance)
                    .containsExactly(firstHandler);

            firstInjector.getInstance(OpenTelemetry.class);
            secondInjector.getInstance(OpenTelemetry.class);
            Logger.get("test.airlift.multiple-bootstrap").info("multiple bootstrap log");
            assertThat(loggerProvider.forceFlush().join(10, SECONDS).isSuccess()).isTrue();
            assertExportedOnce(exporter, "multiple bootstrap log");
            assertExportedOnce(sharedCustomExporter, "multiple bootstrap log");
            assertExportedOnce(secondCustomExporter, "multiple bootstrap log");
        }
        finally {
            secondInjector.getInstance(LifeCycleManager.class).stop();
            firstInjector.getInstance(LifeCycleManager.class).stop();
            removeOpenTelemetryLogHandlers();
            loggerProvider.shutdown().join(10, SECONDS);
        }
    }

    @Test
    @Order(3)
    public void testDisabledLoggingDoesNotCreateHandler()
    {
        AtomicBoolean logRecordProcessorCreated = new AtomicBoolean();
        OpenTelemetryLoggingModule loggingModule = new OpenTelemetryLoggingModule(
                "test-service",
                "test-version",
                (_, _) -> {
                    logRecordProcessorCreated.set(true);
                    return SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create());
                });

        Injector injector = new Bootstrap(
                new TestingNodeModule("testenv"),
                new OpenTelemetryModule("test-service", "test-version"),
                loggingModule)
                .quiet()
                .initialize();

        try {
            assertThat(logRecordProcessorCreated).isFalse();
            assertThat(injector.getInstance(Key.get(new TypeLiteral<Optional<OpenTelemetryLogHandler>>() {}))).isEmpty();
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    private static void assertHandlerIsExported(MBeanExporter mbeanExporter, OpenTelemetryLogHandler logHandler)
    {
        assertThat(mbeanExporter.getManagedObjectExports().keySet())
                .filteredOn(name -> mbeanExporter.getExportedObject(name).orElseThrow() == logHandler)
                .hasSize(1);
    }

    private static void assertExportedOnce(InMemoryLogRecordExporter exporter, String message)
    {
        assertThat(exporter.getFinishedLogRecordItems())
                .filteredOn(record -> record.getBodyValue().asString().equals(message))
                .hasSize(1);
    }

    @Test
    @Order(4)
    public void testEnabledLoggingRequiresLoggingInitialization()
    {
        assertThatThrownBy(() -> new Bootstrap(
                new TestingNodeModule("testenv"),
                new OpenTelemetryModule("test-service", "test-version"),
                new OpenTelemetryLoggingModule("test-service", "test-version"))
                .quiet()
                .doNotInitializeLogging()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .setOptionalConfigurationProperty("node.id", "testnode")
                .initialize())
                .hasMessageContaining("OpenTelemetryLoggingModule requires Bootstrap logging initialization");
    }

    @Test
    @Order(5)
    public void testEnabledLoggingRequiresNodeId()
    {
        assertThatThrownBy(() -> new Bootstrap(
                new TestingNodeModule("testenv"),
                new OpenTelemetryModule("test-service", "test-version"),
                new OpenTelemetryLoggingModule("test-service", "test-version"))
                .quiet()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .initialize())
                .hasRootCauseMessage("log.otlp.enabled=true requires node.id to be configured");
    }

    private static void removeOpenTelemetryLogHandlers()
    {
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof OpenTelemetryLogHandler) {
                rootLogger.removeHandler(handler);
                handler.close();
            }
        }
    }
}
