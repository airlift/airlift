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
import org.eclipse.jetty.server.HttpChannel.Listener;
import org.eclipse.jetty.server.Request;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class HttpServerChannelListener
        implements Listener
{
    private static final String REQUEST_BEGIN_ATTRIBUTE = HttpServerChannelListener.class.getName() + ".begin";
    private static final String REQUEST_BEGIN_TO_DISPATCH_ATTRIBUTE = HttpServerChannelListener.class.getName() + ".begin_to_dispatch";
    private static final String REQUEST_BEGIN_TO_END_ATTRIBUTE = HttpServerChannelListener.class.getName() + ".begin_to_end";
    private static final String RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE = HttpServerChannelListener.class.getName() + ".response_content_timestamps";

    private final DelimitedRequestLog logger;

    public HttpServerChannelListener(DelimitedRequestLog logger)
    {
        this.logger = requireNonNull(logger, "logger is null");
    }

    @Override
    public void onRequestBegin(Request request)
    {
        request.setAttribute(REQUEST_BEGIN_ATTRIBUTE, System.nanoTime());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBeforeDispatch(Request request)
    {
        long requestBeginTime = (Long) request.getAttribute(REQUEST_BEGIN_ATTRIBUTE);
        request.setAttribute(REQUEST_BEGIN_TO_DISPATCH_ATTRIBUTE, System.nanoTime() - requestBeginTime);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onRequestEnd(Request request)
    {
        long requestBeginTime = (Long) request.getAttribute(REQUEST_BEGIN_ATTRIBUTE);
        request.setAttribute(REQUEST_BEGIN_TO_END_ATTRIBUTE, System.nanoTime() - requestBeginTime);
    }

    @Override
    public void onResponseBegin(Request request)
    {
        if (request.getAttribute(REQUEST_BEGIN_TO_END_ATTRIBUTE) == null) {
            onRequestEnd(request);
        }
        request.setAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE, new ArrayList<Long>());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onResponseContent(Request request, ByteBuffer content)
    {
        List<Long> contentTimestamps = (List<Long>) request.getAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE);
        contentTimestamps.add(System.nanoTime());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onComplete(Request request)
    {
        List<Long> contentTimestamps = (List<Long>) request.getAttribute(RESPONSE_CONTENT_TIMESTAMPS_ATTRIBUTE);
        long firstToLastContentTimeInMillis = -1;
        if (contentTimestamps.size() > 0) {
            firstToLastContentTimeInMillis = NANOSECONDS.toMillis(contentTimestamps.get(contentTimestamps.size() - 1) - contentTimestamps.get(0));
        }
        long beginToDispatchMillis = NANOSECONDS.toMillis((Long) request.getAttribute(REQUEST_BEGIN_TO_DISPATCH_ATTRIBUTE));
        long beginToEndMillis = NANOSECONDS.toMillis((Long) request.getAttribute(REQUEST_BEGIN_TO_END_ATTRIBUTE));
        logger.log(request,
                request.getResponse(),
                beginToDispatchMillis,
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
