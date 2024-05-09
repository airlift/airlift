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

import io.airlift.http.server.jetty.RequestTiming;
import jakarta.annotation.Nullable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

import static java.util.Objects.requireNonNull;

public class DispatchingRequestLogHandler
        implements RequestLog
{
    private final DelimitedRequestLog logger;
    private final RequestStats stats;

    public DispatchingRequestLogHandler(@Nullable DelimitedRequestLog logger, RequestStats stats)
    {
        this.logger = logger;
        this.stats = requireNonNull(stats, "stats is null");
    }

    @Override
    public void log(Request request, Response response)
    {
        RequestTiming timings = RequestTimingEventHandler.timings(request);
        if (logger != null) {
            logger.log(request, response, timings);
        }
        stats.record(Request.getContentBytesRead(request), Response.getContentBytesWritten(response), timings.timeToCompletion());
    }
}
