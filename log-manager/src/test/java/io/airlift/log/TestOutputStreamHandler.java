package io.airlift.log;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.LogRecord;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.log.Level.fromJulLevel;
import static java.time.temporal.ChronoUnit.NANOS;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestOutputStreamHandler
{
    @Test
    public void testLoggingToJsonFormat()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> logAnnotations = ImmutableMap.of("key1", "value1");
        OutputStreamHandler handler = new OutputStreamHandler(new PrintStream(out), new JsonFormatter(logAnnotations));
        LogRecord logRecord = new LogRecord(Level.INFO.toJulLevel(), "Test message");
        handler.publish(logRecord);

        String consoleMessage = out.toString();
        JsonRecord jsonRecord = jsonCodec(JsonRecord.class).fromJson(consoleMessage);
        assertThat(consoleMessage).as("Log lines should end with newline").endsWith("\n");

        assertThat(jsonRecord.getTimestamp().truncatedTo(NANOS)).as("Ensure timestamps between the original LogRecord and Json are equal to the nano").isEqualTo(logRecord.getInstant().truncatedTo(NANOS));
        assertThat(jsonRecord.getThread()).isEqualTo(Thread.currentThread().getName());
        assertThat(jsonRecord.getLevel()).isEqualTo(fromJulLevel(logRecord.getLevel()));
        assertThat(jsonRecord.getLoggerName()).isEqualTo(logRecord.getLoggerName());
        assertThat(jsonRecord.getMessage()).isEqualTo(logRecord.getMessage());
        assertThat(jsonRecord.getLogAnnotations()).isEqualTo(logAnnotations);
    }

    @Test
    public void testLoggingToJsonFormatWithException()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> logAnnotations = ImmutableMap.of("key1", "value1");
        Exception testException = new RuntimeException("Test Exception");
        OutputStreamHandler handler = new OutputStreamHandler(new PrintStream(out), new JsonFormatter(logAnnotations));
        LogRecord logRecord = new LogRecord(Level.DEBUG.toJulLevel(), "Test message");
        logRecord.setThrown(testException);
        handler.publish(logRecord);

        String consoleMessage = out.toString();
        Map<String, Object> jsonMap = mapJsonCodec(String.class, Object.class).fromJson(consoleMessage);
        JsonRecord jsonRecord = jsonCodec(JsonRecord.class).fromJson(consoleMessage);

        assertThat(consoleMessage).as("Log lines should end with newline").endsWith("\n");

        assertThat(jsonRecord.getTimestamp().truncatedTo(NANOS)).as("Ensure timestamps between the original LogRecord and Json are equal to the nano").isEqualTo(logRecord.getInstant().truncatedTo(NANOS));

        assertThat(jsonRecord.getThread()).isEqualTo(Thread.currentThread().getName());
        assertThat(jsonRecord.getLevel()).isEqualTo(fromJulLevel(logRecord.getLevel()));
        assertThat(jsonRecord.getLoggerName()).isEqualTo(logRecord.getLoggerName());
        assertThat(jsonRecord.getMessage()).isEqualTo(logRecord.getMessage());

        assertThat(jsonMap.get("throwableClass")).isEqualTo(testException.getClass().getName());
        assertThat(jsonMap.get("throwableMessage")).isEqualTo(testException.getMessage());
        assertThat(jsonMap.get("stackTrace")).isEqualTo(getStackTraceAsString(testException));
    }
}
