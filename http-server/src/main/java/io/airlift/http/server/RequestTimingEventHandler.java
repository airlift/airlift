package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import io.airlift.http.server.jetty.RequestTiming;
import io.airlift.units.Duration;
import jakarta.annotation.Nullable;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.EventsHandler;
import org.eclipse.jetty.util.NanoTime;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.units.Duration.succinctNanos;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

class RequestTimingEventHandler
        extends EventsHandler
{
    static final String REQUEST_HANDLE_STARTED_ATTRIBUTE = RequestTimingEventHandler.class.getName() + ".handle_begin";
    static final String REQUEST_HANDLE_ENDED_ATTRIBUTE = RequestTimingEventHandler.class.getName() + ".handle_end";
    static final String RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE = RequestTimingEventHandler.class.getName() + ".content_write_begin";
    static final String RESPONSE_CONTENT_WRITE_END_ATTRIBUTE = RequestTimingEventHandler.class.getName() + ".content_write_end";

    private static final Object MARKER = new Object();

    public RequestTimingEventHandler(Handler handler)
    {
        super(handler);
    }

    @Override
    protected void onResponseWrite(Request request, boolean last, ByteBuffer content)
    {
        // Called before actual write takes place
        request.setAttribute(RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE + "." + NanoTime.now(), MARKER);
    }

    @Override
    protected void onResponseWriteComplete(Request request, Throwable failure)
    {
        // Called after actual write is completed
        request.setAttribute(RESPONSE_CONTENT_WRITE_END_ATTRIBUTE + "." + NanoTime.now(), MARKER);
    }

    @Override
    protected void onBeforeHandling(Request request)
    {
        // Called before handing over request down in the stack
        request.setAttribute(REQUEST_HANDLE_STARTED_ATTRIBUTE, NanoTime.now());
    }

    @Override
    protected void onResponseTrailersComplete(Request request, HttpFields trailers)
    {
        // Called after request was processed and final write happened
        request.setAttribute(REQUEST_HANDLE_ENDED_ATTRIBUTE, NanoTime.now());
    }

    public static RequestTiming timings(Request request)
    {
        long requestStarted = request.getBeginNanoTime();
        return new RequestTiming(
                Instant.ofEpochMilli(Request.getTimeStamp(request)),
                // Timestamps are ordered monotonically
                elapsedMillis(requestStarted, request.getHeadersNanoTime()), // Time from the start of the request until headers are parsed
                elapsedMillis(requestStarted, getRequestBeginToHandle(request.asAttributeMap())), // Time from the start of the request until before it is handled
                elapsedMillis(requestStarted, getFirstByte(request.asAttributeMap())), // Time from the start of the request until before first write
                elapsedMillis(requestStarted, getLastByte(request.asAttributeMap())), // Time from the start of the request until after last write
                elapsedMillis(requestStarted, getRequestBeginToEnd(request.asAttributeMap())), // Time from the start of the request until it's completed
                processContentTimestamps(getContentWriteBeginTimestamps(request.asAttributeMap())));
    }

    private static long getFirstByte(Map<String, Object> attributes)
    {
        List<Long> writeBeginTimestamps = getContentWriteBeginTimestamps(attributes);
        if (writeBeginTimestamps.isEmpty()) {
            return getRequestBeginToHandle(attributes);
        }
        return writeBeginTimestamps.getFirst();
    }

    private static long getLastByte(Map<String, Object> attributes)
    {
        List<Long> writeEndTimestamps = getContentWriteEndTimestamps(attributes);
        if (writeEndTimestamps.isEmpty()) {
            return getRequestBeginToEnd(attributes);
        }
        return writeEndTimestamps.getLast();
    }

    static long getRequestBeginToHandle(Map<String, Object> attributes)
    {
        return (long) firstNonNull(attributes.get(REQUEST_HANDLE_STARTED_ATTRIBUTE), 0L);
    }

    static long getRequestBeginToEnd(Map<String, Object> attributes)
    {
        return (long) firstNonNull(attributes.get(REQUEST_HANDLE_ENDED_ATTRIBUTE), NanoTime.now());
    }

    static List<Long> getContentWriteBeginTimestamps(Map<String, Object> attributes)
    {
        ImmutableList.Builder<Long> builder = ImmutableList.builder();
        for (String attribute : attributes.keySet()) {
            if (attribute.startsWith(RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE)) {
                String nanoTime = attribute.substring(RESPONSE_CONTENT_WRITE_BEGIN_ATTRIBUTE.length() + 1);
                builder.add(Long.valueOf(nanoTime));
            }
        }

        return Ordering.natural().sortedCopy(builder.build());
    }

    static List<Long> getContentWriteEndTimestamps(Map<String, Object> attributes)
    {
        ImmutableList.Builder<Long> builder = ImmutableList.builder();
        for (String attribute : attributes.keySet()) {
            if (attribute.startsWith(RESPONSE_CONTENT_WRITE_END_ATTRIBUTE)) {
                String nanoTime = attribute.substring(RESPONSE_CONTENT_WRITE_END_ATTRIBUTE.length() + 1);
                builder.add(Long.valueOf(nanoTime));
            }
        }
        return Ordering.natural().sortedCopy(builder.build());
    }

    /**
     * Calculate the summary statistics for the interarrival time of the onResponseContent callbacks.
     */
    @Nullable
    private static DoubleSummaryStats processContentTimestamps(List<Long> contentTimestamps)
    {
        requireNonNull(contentTimestamps, "contentTimestamps is null");

        // no content (HTTP 204) or there was a single response chunk (so no interarrival time)
        if (contentTimestamps.isEmpty() || contentTimestamps.size() == 1) {
            return null;
        }

        DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
        long previousTimestamp = contentTimestamps.get(0);
        for (int i = 1; i < contentTimestamps.size(); i++) {
            long timestamp = contentTimestamps.get(i);
            statistics.accept(elapsedMillis(timestamp, previousTimestamp).toMillis());
            previousTimestamp = timestamp;
        }
        return new DoubleSummaryStats(statistics);
    }

    private static Duration elapsedMillis(long beginNanoTime, long endNanoTime)
    {
        return succinctNanos(max(0, NanoTime.elapsed(beginNanoTime, endNanoTime)));
    }
}
