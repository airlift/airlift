package io.airlift.log;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Map;
import java.util.logging.LogRecord;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static java.time.temporal.ChronoUnit.NANOS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

public class TestJsonFormatter
{
    @Test
    public void testMinimalJsonErrorLogLine()
    {
        JsonRecord original = new JsonRecord(Instant.now(), Level.DEBUG, "thread-0", "TestLogger", "Test Log Message", new Exception("Test Exception 1"), ImmutableMap.of());

        RuntimeException exception = new RuntimeException("Test Exception 2");
        String minimalJsonErrorLogLine = (new JsonFormatter(ImmutableMap.of())).minimalJsonErrorLogLine(original, exception);

        assertThat(minimalJsonErrorLogLine).as("Log lines should end with newline").endsWith("\n");

        assertEquals(
                jsonCodec(JsonRecord.class).fromJson(minimalJsonErrorLogLine),
                new JsonRecord(original.getTimestamp(), Level.ERROR, null, null, exception.getMessage(), null, ImmutableMap.of()));
    }

    @Test
    public void testRoundTrip()
    {
        JsonRecord original = new JsonRecord(Instant.now(), Level.DEBUG, "thread-0", "TestLogger", "Test Log Message", new Exception("Test Exception 1"), ImmutableMap.of());

        assertEquals(
                jsonCodec(JsonRecord.class).fromJson(jsonCodec(JsonRecord.class).toJson(original)),
                new JsonRecord(original.getTimestamp(), original.getLevel(), original.getThread(), original.getLoggerName(), original.getMessage(), null, ImmutableMap.of()));
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

        assertEquals(jsonRecord.getTimestamp().truncatedTo(NANOS), record.getInstant().truncatedTo(NANOS), "Ensure timestamps between the original LogRecord and Json are equal to the nano");

        assertEquals(jsonRecord.getThread(), Thread.currentThread().getName());
        assertEquals(jsonRecord.getLevel(), Level.fromJulLevel(record.getLevel()));
        assertEquals(jsonRecord.getLoggerName(), record.getLoggerName());
        assertEquals(jsonRecord.getMessage(), record.getMessage());

        assertEquals(jsonMap.get("throwableClass"), testException.getClass().getName());
        assertEquals(jsonMap.get("throwableMessage"), testException.getMessage());
        assertEquals(jsonMap.get("stackTrace"), getStackTraceAsString(testException));
    }

    @Test
    public void testLogAnnotations()
    {
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");
        Map<String, String> logAnnotations = ImmutableMap.of("foo", "apple", "bar", "banana");

        String logMessage = (new JsonFormatter(logAnnotations)).format(record);

        Map<String, Object> jsonMap = mapJsonCodec(String.class, Object.class).fromJson(logMessage);
        JsonRecord jsonRecord = jsonCodec(JsonRecord.class).fromJson(logMessage);

        assertEquals(jsonRecord.getMessage(), record.getMessage());
        assertThat(jsonMap.get("annotations")).asInstanceOf(InstanceOfAssertFactories.map(String.class, String.class)).containsExactlyEntriesOf(logAnnotations);
    }
}
