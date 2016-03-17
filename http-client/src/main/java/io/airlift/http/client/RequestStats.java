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
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import io.airlift.stats.CounterStat;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeStat;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.inject.Inject;

import static io.airlift.http.client.HttpStatus.familyForStatusCode;

@Beta
public class RequestStats
{
    private final CounterStat allResponse = new CounterStat();
    private final CounterStat informationalResponse = new CounterStat();
    private final CounterStat successfulResponse = new CounterStat();
    private final CounterStat redirectionResponse = new CounterStat();
    private final CounterStat clientErrorResponse = new CounterStat();
    private final CounterStat serverErrorResponse = new CounterStat();

    private final CounterStat requestFailed = new CounterStat();
    private final CounterStat requestCanceled = new CounterStat();

    private final TimeStat requestTime = new TimeStat();
    private final TimeStat responseTime = new TimeStat();
    private final DistributionStat readBytes = new DistributionStat();
    private final DistributionStat writtenBytes = new DistributionStat();

    @Inject
    public RequestStats()
    {
    }

    public void recordResponseReceived(String method,
            int responseCode,
            long requestSizeInBytes,
            long responseSizeInBytes,
            Duration requestProcessingTime,
            Duration responseProcessingTime)
    {
        requestTime.add(requestProcessingTime);
        responseTime.add(responseProcessingTime);
        readBytes.add(responseSizeInBytes);
        writtenBytes.add(requestSizeInBytes);

        allResponse.update(1);
        switch(familyForStatusCode(responseCode)) {
            case INFORMATIONAL:
                informationalResponse.update(1);
                break;
            case SUCCESSFUL:
                successfulResponse.update(1);
                break;
            case REDIRECTION:
                redirectionResponse.update(1);
                break;
            case CLIENT_ERROR:
                clientErrorResponse.update(1);
                break;
            case SERVER_ERROR:
                serverErrorResponse.update(1);
                break;
        }
    }

    public void recordRequestFailed()
    {
        requestFailed.update(1);
    }

    public void recordRequestCanceled()
    {
        requestCanceled.update(1);
    }

    @Managed
    @Nested
    public CounterStat getAllResponse()
    {
        return allResponse;
    }

    @Managed
    @Nested
    public CounterStat get1xxResponse()
    {
        return informationalResponse;
    }

    @Managed
    @Nested
    public CounterStat get2xxResponse()
    {
        return successfulResponse;
    }

    @Managed
    @Nested
    public CounterStat get3xxResponse()
    {
        return redirectionResponse;
    }

    @Managed
    @Nested
    public CounterStat get4xxResponse()
    {
        return clientErrorResponse;
    }

    @Managed
    @Nested
    public CounterStat get5xxResponse()
    {
        return serverErrorResponse;
    }

    @Managed
    @Nested
    public CounterStat getRequestFailed()
    {
        return requestFailed;
    }

    @Managed
    @Nested
    public CounterStat getRequestCanceled()
    {
        return requestCanceled;
    }

    @Managed
    @Nested
    public TimeStat getRequestTime()
    {
        return requestTime;
    }

    @Managed
    @Nested
    public TimeStat getResponseTime()
    {
        return responseTime;
    }

    @Managed
    @Nested
    public DistributionStat getReadBytes()
    {
        return readBytes;
    }

    @Managed
    @Nested
    public DistributionStat getWrittenBytes()
    {
        return writtenBytes;
    }
}
