/*
 * Copyright 2010 Proofpoint, Inc.
 *
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

import io.airlift.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;

public class StatsRecordingHandler
        implements RequestLog
{
    private final RequestStats stats;

    public StatsRecordingHandler(RequestStats stats)
    {
        this.stats = stats;
    }

    @Override
    public void log(Request request, Response response)
    {
        Duration requestTime = new Duration(max(0, System.currentTimeMillis() - request.getTimeStamp()), TimeUnit.MILLISECONDS);

        long dispatchTime = request.getTimeStamp();

        Duration schedulingDelay = new Duration(max(0, dispatchTime - request.getTimeStamp()), TimeUnit.MILLISECONDS);

        stats.record(request.getMethod(), response.getStatus(), request.getContentRead(), response.getContentCount(), schedulingDelay, requestTime);
    }
}
