package io.airlift.opentelemetry;

import io.airlift.bootstrap.LoggingBootstrapContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import org.weakref.jmx.Managed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;

final class OpenTelemetryLogHandler
        extends Handler
{
    private static final String UNKNOWN_LOGGER_NAME = "UNKNOWN";
    private static final AttributeKey<String> THREAD_NAME = stringKey("thread.name");
    private static final long FLUSH_TIMEOUT_SECONDS = 10;
    private static final Formatter MESSAGE_FORMATTER = new SimpleFormatter();

    private final SdkLoggerProvider loggerProvider;
    private final Optional<LateBindingLogRecordProcessor> lateBindingLogRecordProcessor;
    private final Optional<MetricProducer> metricProducer;
    private final Attributes logAnnotations;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong submittedRecords = new AtomicLong();
    private final AtomicLong failedEmitAttempts = new AtomicLong();
    private final AtomicLong openTelemetryInternalRecords = new AtomicLong();

    OpenTelemetryLogHandler(Resource resource, LogRecordProcessor logRecordProcessor, MetricProducer metricProducer, LoggingBootstrapContext context)
    {
        this(new LateBindingLogRecordProcessor(logRecordProcessor), metricProducer, resource, context);
    }

    private OpenTelemetryLogHandler(LateBindingLogRecordProcessor logRecordProcessor, MetricProducer metricProducer, Resource resource, LoggingBootstrapContext context)
    {
        this(SdkLoggerProvider.builder()
                        .setResource(requireNonNull(resource, "resource is null"))
                        .addLogRecordProcessor(logRecordProcessor)
                        .build(),
                requireNonNull(context, "context is null").getLogAnnotations(),
                requireNonNull(context, "context is null").createErrorManager(),
                Optional.of(logRecordProcessor),
                Optional.of(requireNonNull(metricProducer, "metricProducer is null")));
    }

    OpenTelemetryLogHandler(SdkLoggerProvider loggerProvider, Map<String, String> logAnnotations, ErrorManager errorManager)
    {
        this(loggerProvider, logAnnotations, errorManager, Optional.empty(), Optional.empty());
    }

    private OpenTelemetryLogHandler(
            SdkLoggerProvider loggerProvider,
            Map<String, String> logAnnotations,
            ErrorManager errorManager,
            Optional<LateBindingLogRecordProcessor> lateBindingLogRecordProcessor,
            Optional<MetricProducer> metricProducer)
    {
        this.loggerProvider = requireNonNull(loggerProvider, "loggerProvider is null");
        this.lateBindingLogRecordProcessor = requireNonNull(lateBindingLogRecordProcessor, "lateBindingLogRecordProcessor is null");
        this.metricProducer = requireNonNull(metricProducer, "metricProducer is null");
        this.logAnnotations = logAnnotationAttributes(logAnnotations);
        setErrorManager(requireNonNull(errorManager, "errorManager is null"));
        setLevel(java.util.logging.Level.ALL);
    }

    SdkLoggerProvider getLoggerProvider()
    {
        return loggerProvider;
    }

    LogRecordProcessor getInitialLogRecordProcessor()
    {
        return lateBindingLogRecordProcessor
        .orElseThrow(() -> new IllegalStateException("OpenTelemetry log handler does not expose its initial processor"))
        .initialProcessor;
    }

    MetricProducer getMetricProducer()
    {
        return metricProducer.orElseThrow(() -> new IllegalStateException("OpenTelemetry log handler does not expose its metric producer"));
    }

    void addLogRecordProcessors(Collection<LogRecordProcessor> logRecordProcessors)
    {
        requireNonNull(logRecordProcessors, "logRecordProcessors is null");
        if (logRecordProcessors.isEmpty()) {
            return;
        }
        lateBindingLogRecordProcessor
                .orElseThrow(() -> new IllegalStateException("OpenTelemetry log handler does not support late processor binding"))
                .addLogRecordProcessors(logRecordProcessors);
    }

    @Override
    public void publish(LogRecord record)
    {
        try {
            if (!isLoggable(record) || closed.get()) {
                return;
            }
            if (isOpenTelemetryInternal(record)) {
                openTelemetryInternalRecords.incrementAndGet();
                reportOpenTelemetryInternal(record);
                return;
            }

            emit(record, Context.current(), Thread.currentThread().getName());
        }
        catch (Exception e) {
            failedEmitAttempts.incrementAndGet();
            reportError("Unexpected OpenTelemetry log handler exception", e, GENERIC_FAILURE);
        }
    }

    private static boolean isOpenTelemetryInternal(LogRecord record)
    {
        String loggerName = record.getLoggerName();
        return loggerName != null && loggerName.startsWith("io.opentelemetry.");
    }

    private void reportOpenTelemetryInternal(LogRecord record)
    {
        reportError(
                "OpenTelemetry internal log record from %s: %s: %s".formatted(
                        requireNonNullElse(record.getLoggerName(), UNKNOWN_LOGGER_NAME),
                        record.getLevel(),
                        record.getMessage()),
                toException(record.getThrown()),
                GENERIC_FAILURE);
    }

    private static Exception toException(Throwable throwable)
    {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    private void emit(LogRecord record, Context context, String threadName)
    {
        String loggerName = requireNonNullElse(record.getLoggerName(), UNKNOWN_LOGGER_NAME);
        LogRecordBuilder builder = loggerProvider.get(loggerName)
                .logRecordBuilder()
                .setTimestamp(record.getInstant())
                .setContext(context)
                .setSeverity(severity(record.getLevel()))
                .setSeverityText(severityText(record.getLevel()))
                .setAllAttributes(logAnnotations)
                .setAttribute(THREAD_NAME, threadName);

        if (record.getMessage() != null) {
            builder.setBody(MESSAGE_FORMATTER.formatMessage(record));
        }
        Optional.ofNullable(record.getThrown()).ifPresent(builder::setException);

        builder.emit();
        submittedRecords.incrementAndGet();
    }

    private static Attributes logAnnotationAttributes(Map<String, String> logAnnotations)
    {
        requireNonNull(logAnnotations, "logAnnotations is null");
        if (logAnnotations.isEmpty()) {
            return Attributes.empty();
        }
        AttributesBuilder builder = Attributes.builder();
        logAnnotations.forEach(builder::put);
        return builder.build();
    }

    private static Severity severity(java.util.logging.Level level)
    {
        int levelValue = level.intValue();
        if (levelValue >= java.util.logging.Level.SEVERE.intValue()) {
            return Severity.ERROR;
        }
        if (levelValue >= java.util.logging.Level.WARNING.intValue()) {
            return Severity.WARN;
        }
        if (levelValue >= java.util.logging.Level.INFO.intValue()) {
            return Severity.INFO;
        }
        return Severity.DEBUG;
    }

    private static String severityText(java.util.logging.Level level)
    {
        int levelValue = level.intValue();
        if (levelValue >= java.util.logging.Level.SEVERE.intValue()) {
            return "ERROR";
        }
        if (levelValue >= java.util.logging.Level.WARNING.intValue()) {
            return "WARN";
        }
        if (levelValue >= java.util.logging.Level.INFO.intValue()) {
            return "INFO";
        }
        return "DEBUG";
    }

    @Override
    public void flush()
    {
        try {
            CompletableResultCode result = loggerProvider.forceFlush()
                    .join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.isDone()) {
                reportError("Timed out flushing OpenTelemetry log records", null, FLUSH_FAILURE);
            }
            else if (!result.isSuccess()) {
                reportError("Failed to flush OpenTelemetry log records", toException(result.getFailureThrowable()), FLUSH_FAILURE);
            }
        }
        catch (Exception e) {
            reportError("Failed to flush OpenTelemetry log records", e, FLUSH_FAILURE);
        }
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        flush();
    }

    @Managed
    public long getSubmittedRecords()
    {
        return submittedRecords.get();
    }

    @Managed
    public long getFailedEmitAttempts()
    {
        return failedEmitAttempts.get();
    }

    @Managed
    public long getOpenTelemetryInternalRecords()
    {
        return openTelemetryInternalRecords.get();
    }

    private static final class LateBindingLogRecordProcessor
            implements LogRecordProcessor
    {
        private final LogRecordProcessor initialProcessor;
        private final List<LogRecordProcessor> processors = new ArrayList<>();
        private final Set<LogRecordProcessor> knownProcessors = Collections.newSetFromMap(new IdentityHashMap<>());
        private volatile LogRecordProcessor delegate;

        private LateBindingLogRecordProcessor(LogRecordProcessor initialProcessor)
        {
            this.initialProcessor = requireNonNull(initialProcessor, "initialProcessor is null");
            processors.add(initialProcessor);
            knownProcessors.add(initialProcessor);
            this.delegate = initialProcessor;
        }

        private synchronized void addLogRecordProcessors(Collection<LogRecordProcessor> logRecordProcessors)
        {
            boolean changed = false;
            for (LogRecordProcessor processor : logRecordProcessors) {
                requireNonNull(processor, "logRecordProcessors contains null");
                if (knownProcessors.add(processor)) {
                    processors.add(processor);
                    changed = true;
                }
            }
            if (changed) {
                delegate = LogRecordProcessor.composite(List.copyOf(processors));
            }
        }

        @Override
        public void onEmit(Context context, ReadWriteLogRecord logRecord)
        {
            delegate.onEmit(context, logRecord);
        }

        @Override
        public CompletableResultCode shutdown()
        {
            return delegate.shutdown();
        }

        @Override
        public CompletableResultCode forceFlush()
        {
            return delegate.forceFlush();
        }
    }
}
