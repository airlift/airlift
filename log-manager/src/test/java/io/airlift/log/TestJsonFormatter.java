package io.airlift.log;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static java.time.temporal.ChronoUnit.NANOS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonFormatter
{
    @Test
    public void testMinimalJsonErrorLogLine()
    {
        JsonRecord original = new JsonRecord(
                Instant.now(),
                Level.DEBUG,
                "thread-0",
                "TestLogger",
                "Test Log Message",
                List.of(),
                new Exception("Test Exception 1"),
                Context.root(),
                ImmutableMap.of());

        RuntimeException exception = new RuntimeException("Test Exception 2");
        String minimalJsonErrorLogLine = (new JsonFormatter(ImmutableMap.of())).minimalJsonErrorLogLine(original, exception);

        assertThat(minimalJsonErrorLogLine).as("Log lines should end with newline").endsWith("\n");

        assertThat(jsonCodec(JsonRecord.class).fromJson(minimalJsonErrorLogLine)).isEqualTo(new JsonRecord(
                original.getTimestamp(),
                Level.ERROR,
                null,
                null,
                exception.getMessage(),
                (Object[]) null,
                null,
                Context.root(),
                ImmutableMap.of()));
    }

    @Test
    public void testMinimalRecordFormatting()
    {
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Testing");
        record.setLoggerName("TestLogger");

        assertThat((new JsonFormatter(ImmutableMap.of())).format(record))
                .matches("\\{\"timestamp\":\".*\",\"level\":\"DEBUG\",\"thread\":\".*\",\"logger\":\"TestLogger\",\"message\":\"Testing\"}\n");
    }

    @Test
    public void testRoundTrip()
    {
        JsonRecord original = new JsonRecord(
                Instant.now(),
                Level.DEBUG,
                "thread-0",
                "TestLogger",
                "Test Log Message",
                List.of(),
                new Exception("Test Exception 1"),
                Context.root(),
                ImmutableMap.of());

        assertThat(jsonCodec(JsonRecord.class).fromJson(jsonCodec(JsonRecord.class).toJson(original))).isEqualTo(new JsonRecord(
                original.getTimestamp(),
                original.getLevel(),
                original.getThread(),
                original.getLoggerName(),
                original.getMessage(),
                List.of(),
                null,
                Context.root(),
                ImmutableMap.of()));
    }

    @Test
    public void testLogFormatting()
    {
        Exception testException = new RuntimeException("Test Exception");
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");
        record.setLoggerName("TestLogger");
        record.setThrown(testException);

        String logMessage = (new JsonFormatter(ImmutableMap.of())).format(record);

        Map<String, Object> jsonMap = mapJsonCodec(String.class, Object.class).fromJson(logMessage);
        JsonRecord jsonRecord = jsonCodec(JsonRecord.class).fromJson(logMessage);

        assertThat(logMessage).as("Log lines should end with newline").endsWith("\n");

        assertThat(jsonRecord.getTimestamp().truncatedTo(NANOS)).as("Ensure timestamps between the original LogRecord and Json are equal to the nano").isEqualTo(record.getInstant().truncatedTo(NANOS));

        assertThat(jsonRecord.getThread()).isEqualTo(Thread.currentThread().getName());
        assertThat(jsonRecord.getLevel()).isEqualTo(Level.fromJulLevel(record.getLevel()));
        assertThat(jsonRecord.getLoggerName()).isEqualTo(record.getLoggerName());
        assertThat(jsonRecord.getMessage()).isEqualTo(record.getMessage());

        assertThat(jsonMap.get("throwableClass")).isEqualTo(testException.getClass().getName());
        assertThat(jsonMap.get("throwableMessage")).isEqualTo(testException.getMessage());
        assertThat(jsonMap.get("stackTrace")).isEqualTo(getStackTraceAsString(testException));
    }

    @Test
    public void testLogContext()
    {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        try (tracerProvider) {
            Span span = tracerProvider.get("test")
                    .spanBuilder("test-span")
                    .startSpan();

            String traceId = span.getSpanContext().getTraceId();
            String spanId = span.getSpanContext().getSpanId();
            String traceFlags = span.getSpanContext().getTraceFlags().asHex();

            assertThat(TraceId.isValid(traceId)).isTrue();
            assertThat(SpanId.isValid(spanId)).isTrue();
            assertThat(traceFlags).isEqualTo("01");

            LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");

            String logMessage;
            try (var ignored = span.makeCurrent()) {
                logMessage = (new JsonFormatter(ImmutableMap.of())).format(record);
            }
            finally {
                span.end();
            }

            Map<String, Object> jsonMap = mapJsonCodec(String.class, Object.class).fromJson(logMessage);

            assertThat(jsonMap.get("traceId")).isEqualTo(traceId);
            assertThat(jsonMap.get("spanId")).isEqualTo(spanId);
            assertThat(jsonMap.get("traceFlags")).isEqualTo(traceFlags);
        }
    }

    @Test
    public void testLogAnnotations()
    {
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");
        Map<String, String> logAnnotations = ImmutableMap.of("foo", "apple", "bar", "banana");

        String logMessage = (new JsonFormatter(logAnnotations)).format(record);

        Map<String, Object> jsonMap = mapJsonCodec(String.class, Object.class).fromJson(logMessage);
        JsonRecord jsonRecord = jsonCodec(JsonRecord.class).fromJson(logMessage);

        assertThat(jsonRecord.getMessage()).isEqualTo(record.getMessage());
        assertThat(jsonMap.get("annotations")).asInstanceOf(InstanceOfAssertFactories.map(String.class, String.class)).containsExactlyEntriesOf(logAnnotations);
    }
}
