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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.stats.CounterStat;
import com.proofpoint.stats.MeterStat;
import com.proofpoint.stats.TimedStat;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Beta
public class RequestStats
{
    private final CounterStat request;
    private final TimedStat requestTime;
    private final TimedStat responseTime;
    private final MeterStat readBytes;
    private final MeterStat writtenBytes;
    private final ScheduledExecutorService executor;

    @Inject
    public RequestStats()
    {
        executor = new ScheduledThreadPoolExecutor(2, new ThreadFactoryBuilder().setNameFormat("RequestStatsTicker-%s").setDaemon(true).build());

        request = new CounterStat(executor);
        requestTime = new TimedStat();
        responseTime = new TimedStat();
        readBytes = new MeterStat(executor);
        writtenBytes = new MeterStat(executor);

        request.start();
        readBytes.start();
        writtenBytes.start();
    }

    @PreDestroy
    public void shutdown()
    {
        request.stop();
        readBytes.stop();
        writtenBytes.stop();
        executor.shutdown();
    }

    public void record(String method,
            int responseCode,
            long requestSizeInBytes,
            long responseSizeInBytes,
            Duration requestProcessingTime,
            Duration responseProcessingTime)
    {
        request.update(1);
        if (requestProcessingTime != null) {
            requestTime.addValue(requestProcessingTime);
        }
        if (requestProcessingTime != null) {
            responseTime.addValue(responseProcessingTime);
        }
        readBytes.update(responseSizeInBytes);
        writtenBytes.update(requestSizeInBytes);
    }

    @Managed
    @Flatten
    public CounterStat getRequest()
    {
        return request;
    }

    @Managed
    @Nested
    public TimedStat getRequestTime()
    {
        return requestTime;
    }

    @Managed
    @Nested
    public TimedStat getResponseTime()
    {
        return responseTime;
    }

    @Managed
    @Nested
    public MeterStat getReadBytes()
    {
        return readBytes;
    }

    @Managed
    @Nested
    public MeterStat getWrittenBytes()
    {
        return writtenBytes;
    }
}
