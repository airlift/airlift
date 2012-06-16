package io.airlift.http.server;

import io.airlift.stats.CounterStat;
import io.airlift.stats.MeterStat;
import io.airlift.stats.TimedStat;
import io.airlift.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.inject.Inject;

public class RequestStats
{
    private final CounterStat request;
    private final TimedStat requestTime;
    private final MeterStat readBytes;
    private final MeterStat writtenBytes;

    @Inject
    public RequestStats()
    {
        request = new CounterStat();
        requestTime = new TimedStat();
        readBytes = new MeterStat();
        writtenBytes = new MeterStat();
    }

    public void record(String method, int responseCode, long requestSizeInBytes, long responseSizeInBytes, Duration schedulingDelay, Duration requestProcessingTime)
    {
        request.update(1);
        requestTime.addValue(requestProcessingTime);
        readBytes.update(requestSizeInBytes);
        writtenBytes.update(responseSizeInBytes);
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
