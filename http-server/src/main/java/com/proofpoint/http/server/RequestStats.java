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
package com.proofpoint.http.server;

import com.proofpoint.stats.DistributionStat;
import com.proofpoint.stats.TimeStat;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Nested;

import javax.inject.Inject;

public class RequestStats
{
    private final TimeStat requestTime;
    private final DistributionStat readBytes;
    private final DistributionStat writtenBytes;

    @Inject
    public RequestStats()
    {
        requestTime = new TimeStat();
        readBytes = new DistributionStat();
        writtenBytes = new DistributionStat();
    }

    public void record(String method, int responseCode, long requestSizeInBytes, long responseSizeInBytes, Duration schedulingDelay, Duration requestProcessingTime)
    {
        requestTime.add(requestProcessingTime);
        readBytes.add(requestSizeInBytes);
        writtenBytes.add(responseSizeInBytes);
    }

    @Nested
    public TimeStat getRequestTime()
    {
        return requestTime;
    }

    @Nested
    public DistributionStat getReadBytes()
    {
        return readBytes;
    }

    @Nested
    public DistributionStat getWrittenBytes()
    {
        return writtenBytes;
    }
}
