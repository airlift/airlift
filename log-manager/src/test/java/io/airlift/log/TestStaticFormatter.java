package io.airlift.log;

import io.opentelemetry.api.baggage.Baggage;
import org.junit.jupiter.api.Test;

import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

public class TestStaticFormatter
{
    @Test
    public void testFormatBaggage()
    {
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");
        Baggage baggage = Baggage.builder()
                .put("orderId", "42")
                .put("tenant", "acme")
                .build();

        String logMessage;
        try (var ignored = baggage.makeCurrent()) {
            logMessage = new StaticFormatter().format(record);
        }

        assertThat(logMessage).contains("\tbaggage=");
        assertThat(logMessage).contains("orderId=42");
        assertThat(logMessage).contains("tenant=acme");
    }

    @Test
    public void testNoBaggageOmitsSegment()
    {
        LogRecord record = new LogRecord(Level.DEBUG.toJulLevel(), "Test Log Message");

        String logMessage = new StaticFormatter().format(record);

        assertThat(logMessage).doesNotContain("baggage=");
    }
}
