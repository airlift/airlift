package io.airlift.http.server;

import io.airlift.http.server.jetty.RequestTiming;
import io.airlift.units.Duration;
import org.eclipse.jetty.server.Request;
import org.mockito.MockedStatic;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.Map;

import static io.airlift.http.server.RequestTimingEventHandler.REQUEST_HANDLE_ENDED_ATTRIBUTE;
import static io.airlift.http.server.RequestTimingEventHandler.REQUEST_HANDLE_STARTED_ATTRIBUTE;
import static io.airlift.http.server.RequestTimingEventHandler.RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE;
import static io.airlift.http.server.RequestTimingEventHandler.RESPONSE_CONTENT_WRITE_END_ATTRIBUTE;
import static io.airlift.http.server.RequestTimingEventHandler.timings;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TestRequestTimingEventHandler
{
    private static final Object MARKER = new Object();

    @Test
    public void testExtractTimings()
    {
        try (MockedStatic<Request> ignored = mockStatic(Request.class, RETURNS_DEEP_STUBS)) {
            Request request = mock(Request.class, RETURNS_DEEP_STUBS);
            Instant now = Instant.now();
            long startNanos = MILLISECONDS.toNanos(now.toEpochMilli());

            when(Request.getTimeStamp(request)).thenReturn(now.toEpochMilli());
            when(request.getBeginNanoTime()).thenReturn(startNanos);
            when(request.getHeadersNanoTime()).thenReturn(startNanos + 100);

            when(request.asAttributeMap()).thenReturn(Map.of(
                    REQUEST_HANDLE_STARTED_ATTRIBUTE, startNanos + 110,
                    RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE + "." + (startNanos + 200), MARKER,
                    RESPONSE_CONTENT_WRITE_END_ATTRIBUTE + "." + (startNanos + 210), MARKER,
                    RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE + "." + (startNanos + 220), MARKER,
                    RESPONSE_CONTENT_WRITE_END_ATTRIBUTE + "." + (startNanos + 250), MARKER,
                    REQUEST_HANDLE_ENDED_ATTRIBUTE, startNanos + 500));

            RequestTiming timings = timings(request);

            assertEquals(timings.requestStarted(), now.truncatedTo(MILLIS));
            assertEquals(timings.timeToDispatch(), Duration.valueOf("100.00ns"));
            assertEquals(timings.timeToHandling(), Duration.valueOf("110.00ns"));
            assertEquals(timings.timeToFirstByte(), Duration.valueOf("200.00ns"));
            assertEquals(timings.timeToLastByte(), Duration.valueOf("250.00ns"));
            assertEquals(timings.timeToCompletion(), Duration.valueOf("500.00ns"));
        }
    }
}
