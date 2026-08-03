package io.airlift.opentelemetry;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.log.Logger;
import io.airlift.node.testing.TestingNodeModule;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.util.Optional;
import java.util.logging.Handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestOpenTelemetryLoggingModule
{
    @Test
    public void testLogHandlerIsBoundExportedAndReused()
    {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        OpenTelemetryLoggingModule loggingModule = new OpenTelemetryLoggingModule(
                "test-service",
                "test-version",
                (_, _) -> SimpleLogRecordProcessor.create(exporter));

        Injector injector = new Bootstrap(
                new TestingNodeModule("testenv"),
                new TestingMBeanModule(),
                new OpenTelemetryModule("test-service", "test-version"),
                loggingModule)
                .quiet()
                .setOptionalConfigurationProperty("log.otlp.enabled", "true")
                .setOptionalConfigurationProperty("node.environment", "testenv")
                .setOptionalConfigurationProperty("node.id", "testnode")
                .initialize();

        try {
            Logger.get("test.airlift.otlp").info("adopted provider log");
            Optional<OpenTelemetryLogHandler> logHandler = injector.getInstance(Key.get(new TypeLiteral<Optional<OpenTelemetryLogHandler>>() {}));
            assertThat(logHandler).isPresent();
            assertHandlerIsExported(injector.getInstance(MBeanExporter.class), logHandler.orElseThrow());

            injector.getInstance(OpenTelemetry.class)
                    .getLogsBridge()
                    .get("test.sdk.otlp")
                    .logRecordBuilder()
                    .setSeverity(Severity.INFO)
                    .setBody("sdk provider log")
                    .emit();

            assertThat(exporter.getFinishedLogRecordItems())
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.airlift.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("adopted provider log");
                    })
                    .anySatisfy(record -> {
                        assertThat(record.getInstrumentationScopeInfo().getName()).isEqualTo("test.sdk.otlp");
                        assertThat(record.getBodyValue().asString()).isEqualTo("sdk provider log");
                    });
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
            removeOpenTelemetryLogHandlers();
        }
    }

    private static void assertHandlerIsExported(MBeanExporter mbeanExporter, OpenTelemetryLogHandler logHandler)
    {
        assertThat(mbeanExporter.getManagedObjectExports().keySet())
                .filteredOn(name -> mbeanExporter.getExportedObject(name).orElseThrow() == logHandler)
                .hasSize(1);
    }

    @Test
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
