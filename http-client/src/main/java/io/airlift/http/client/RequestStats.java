package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.airlift.stats.CounterStat;
import io.airlift.stats.MeterStat;
import io.airlift.stats.TimedStat;
import io.airlift.units.Duration;
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

    @Inject
    public RequestStats()
    {
        request = new CounterStat();
        requestTime = new TimedStat();
        responseTime = new TimedStat();
        readBytes = new MeterStat();
        writtenBytes = new MeterStat();
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
