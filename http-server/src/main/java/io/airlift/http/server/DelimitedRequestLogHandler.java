/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server;

import jakarta.annotation.Nullable;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.EventsHandler;
import org.eclipse.jetty.util.NanoTime;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class DelimitedRequestLogHandler
        extends EventsHandler
        implements RequestLog
{
    public static final String REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE = DelimitedRequestLogHandler.class.getName() + ".begin_to_handle";
    private static final String RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE = DelimitedRequestLogHandler.class.getName() + ".response_content_timestamps";

    private final DelimitedRequestLog logger;

    public DelimitedRequestLogHandler(DelimitedRequestLog logger)
    {
        this.logger = requireNonNull(logger, "logger is null");
    }

    @Override
    protected void onResponseBegin(Request request, int status, HttpFields headers)
    {
        request.setAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE, new ArrayList<Long>());
    }

    @Override
    protected void onResponseWriteComplete(Request request, Throwable failure)
    {
        List<Long> contentTimestamps = Optional.ofNullable(request.getAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE))
                .map(object -> (List<Long>) object)
                .orElseThrow(() -> new IllegalStateException("Request attribute " + RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE + " is not present"));
        contentTimestamps.add(NanoTime.now());
    }

    @Override
    protected void onBeforeHandling(Request request)
    {
        request.setAttribute(REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE, NanoTime.since(request.getBeginNanoTime()));
    }

    @Override
    public void log(Request request, Response response)
    {
        List<Long> contentTimestamps = Optional.ofNullable(request.getAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE))
                .map(object -> (List<Long>) object)
                .orElseGet(() -> (List<Long>) request.setAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE, new ArrayList<Long>()));

        long firstToLastContentTimeInMillis = -1;
        if (!contentTimestamps.isEmpty()) {
            firstToLastContentTimeInMillis = NANOSECONDS.toMillis(contentTimestamps.get(contentTimestamps.size() - 1) - contentTimestamps.get(0));
        }

        long beginToHandleMillis = NANOSECONDS.toMillis((Long) request.getAttribute(REQUEST_BEGIN_TO_HANDLE_ATTRIBUTE));
        long beginToEndMillis = NANOSECONDS.toMillis(NanoTime.since(request.getBeginNanoTime()));

        logger.log(request,
                response,
                beginToHandleMillis,
                beginToEndMillis,
                firstToLastContentTimeInMillis,
                processContentTimestamps(contentTimestamps));
    }

    /**
     * Calculate the summary statistics for the interarrival time of the onResponseContent callbacks.
     */
    @Nullable
    private static DoubleSummaryStats processContentTimestamps(List<Long> contentTimestamps)
    {
        requireNonNull(contentTimestamps, "contentTimestamps is null");

        // no content (HTTP 204) or there was a single response chunk (so no interarrival time)
        if (contentTimestamps.size() == 0 || contentTimestamps.size() == 1) {
            return null;
        }

        DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
        long previousTimestamp = contentTimestamps.get(0);
        for (int i = 1; i < contentTimestamps.size(); i++) {
            long timestamp = contentTimestamps.get(i);
            statistics.accept(NANOSECONDS.toMillis(timestamp - previousTimestamp));
            previousTimestamp = timestamp;
        }
        return new DoubleSummaryStats(statistics);
    }
}
