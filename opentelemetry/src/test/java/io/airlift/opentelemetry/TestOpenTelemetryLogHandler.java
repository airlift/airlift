package io.airlift.opentelemetry;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.ErrorManager;
import java.util.logging.LogRecord;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Map.entry;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class TestOpenTelemetryLogHandler
{
    @Test
    public void testEmitsLogRecord()
    {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()) {
            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, Map.of("pod", "test-pod"), new ErrorManager());

            LogRecord record = new LogRecord(WARNING, "hello otlp");
            record.setLoggerName("test.logger");
            record.setThrown(new IllegalArgumentException("bad value"));

            try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                    .build()) {
                Span span = tracerProvider.get("test")
                        .spanBuilder("test-span")
                        .startSpan();
                try (var ignored = span.makeCurrent()) {
                    handler.publish(record);
                }
                finally {
                    span.end();
                }

                handler.close();

                LogRecordData logRecord = exporter.getFinishedLogRecordItems().stream().collect(onlyElement());
                assertThat(handler.getSubmittedRecords()).isEqualTo(1);
                assertThat(logRecord.getInstrumentationScopeInfo().getName()).isEqualTo("test.logger");
                assertThat(logRecord.getBodyValue().asString()).isEqualTo("hello otlp");
                assertThat(logRecord.getSeverity()).isEqualTo(Severity.WARN);
                assertThat(logRecord.getSeverityText()).isEqualTo("WARN");
                assertThat(logRecord.getSpanContext().getTraceId()).isEqualTo(span.getSpanContext().getTraceId());
                assertThat(logRecord.getSpanContext().getSpanId()).isEqualTo(span.getSpanContext().getSpanId());
                assertThat(logRecord.getAttributes().asMap()).contains(
                        entry(stringKey("thread.name"), Thread.currentThread().getName()),
                        entry(stringKey("pod"), "test-pod"),
                        entry(stringKey("exception.type"), IllegalArgumentException.class.getName()),
                        entry(stringKey("exception.message"), "bad value"));
            }
        }
    }

    @Test
    @Timeout(10)
    public void testSdkBatchProcessorDropsWhenQueueIsFull()
            throws Exception
    {
        BlockingLogRecordExporter exporter = new BlockingLogRecordExporter();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter)
                        .setMaxQueueSize(1)
                        .setMaxExportBatchSize(1)
                        .setScheduleDelay(1, TimeUnit.MILLISECONDS)
                        .build())
                .build()) {
            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, Map.of(), new ErrorManager());

            LogRecord first = new LogRecord(INFO, "first");
            first.setLoggerName("test.logger");
            LogRecord second = new LogRecord(INFO, "second");
            second.setLoggerName("test.logger");
            LogRecord third = new LogRecord(INFO, "third");
            third.setLoggerName("test.logger");

            handler.publish(first);
            assertThat(exporter.awaitExportStarted()).isTrue();

            assertThatNoException().isThrownBy(() -> {
                handler.publish(second);
                handler.publish(third);
            });
            assertThat(handler.getSubmittedRecords()).isEqualTo(3);

            exporter.unblock();
            handler.close();
            assertThat(exporter.getExportedRecords()).isLessThan(3);
        }
    }

    @Test
    public void testSkipsOpenTelemetryInternalLogs()
    {
        InMemoryLogRecordExporter exporter = InMemoryLogRecordExporter.create();
        CapturingErrorManager errorManager = new CapturingErrorManager();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()) {
            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, Map.of(), errorManager);

            LogRecord record = new LogRecord(INFO, "internal");
            record.setLoggerName("io.opentelemetry.exporter.otlp.logs");
            record.setThrown(new IllegalStateException("collector unavailable"));
            handler.publish(record);
            handler.close();

            assertThat(handler.getOpenTelemetryInternalRecords()).isEqualTo(1);
            assertThat(errorManager.message).isEqualTo("OpenTelemetry internal log record from io.opentelemetry.exporter.otlp.logs: INFO: internal");
            assertThat(errorManager.exception).isInstanceOf(IllegalStateException.class);
            assertThat(errorManager.code).isEqualTo(ErrorManager.GENERIC_FAILURE);
            assertThat(exporter.getFinishedLogRecordItems()).isEmpty();
        }
    }

    @Test
    public void testPublishFailureReportsToErrorManager()
    {
        CapturingErrorManager errorManager = new CapturingErrorManager();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create()))
                .build()) {
            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, Map.of(), errorManager);

            LogRecord record = new LogRecord(INFO, "message")
            {
                @Override
                public java.time.Instant getInstant()
                {
                    throw new IllegalStateException("bad record");
                }
            };

            assertThatNoException().isThrownBy(() -> handler.publish(record));

            assertThat(handler.getFailedEmitAttempts()).isEqualTo(1);
            assertThat(errorManager.message).isEqualTo("Unexpected OpenTelemetry log handler exception");
            assertThat(errorManager.exception).isInstanceOf(IllegalStateException.class);
            assertThat(errorManager.code).isEqualTo(ErrorManager.GENERIC_FAILURE);
        }
    }

    @Test
    public void testFlushFailureReportsToErrorManager()
    {
        CapturingErrorManager errorManager = new CapturingErrorManager();
        try (SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(new FailingFlushLogRecordProcessor())
                .build()) {
            OpenTelemetryLogHandler handler = new OpenTelemetryLogHandler(loggerProvider, Map.of(), errorManager);

            handler.flush();

            assertThat(errorManager.message).isEqualTo("Failed to flush OpenTelemetry log records");
            assertThat(errorManager.exception).isInstanceOf(IllegalStateException.class);
            assertThat(errorManager.code).isEqualTo(ErrorManager.FLUSH_FAILURE);
        }
    }

    private static class FailingFlushLogRecordProcessor
            implements LogRecordProcessor
    {
        @Override
        public void onEmit(Context context, ReadWriteLogRecord logRecord) {}

        @Override
        public CompletableResultCode forceFlush()
        {
            return CompletableResultCode.ofExceptionalFailure(new IllegalStateException("flush failed"));
        }

        @Override
        public CompletableResultCode shutdown()
        {
            return CompletableResultCode.ofSuccess();
        }
    }

    private static class BlockingLogRecordExporter
            implements LogRecordExporter
    {
        private final CountDownLatch exportStarted = new CountDownLatch(1);
        private final CountDownLatch unblock = new CountDownLatch(1);
        private int exportedRecords;

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs)
        {
            exportStarted.countDown();
            try {
                unblock.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableResultCode.ofFailure();
            }
            exportedRecords += logs.size();
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush()
        {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown()
        {
            unblock();
            return CompletableResultCode.ofSuccess();
        }

        boolean awaitExportStarted()
                throws InterruptedException
        {
            return exportStarted.await(1, TimeUnit.SECONDS);
        }

        void unblock()
        {
            unblock.countDown();
        }

        int getExportedRecords()
        {
            return exportedRecords;
        }
    }

    private static class CapturingErrorManager
            extends ErrorManager
    {
        private String message;
        private Exception exception;
        private int code;

        @Override
        public void error(String message, Exception exception, int code)
        {
            this.message = message;
            this.exception = exception;
            this.code = code;
        }
    }
}
