package com.proofpoint.http.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.stats.CounterStat;
import com.proofpoint.stats.MeterStat;
import com.proofpoint.stats.TimeStat;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RequestStats
{
    private final CounterStat request;
    private final TimeStat schedulingTime;
    private final TimeStat requestTime;
    private final TimeStat responseTime;
    private final MeterStat readBytes;
    private final MeterStat writtenBytes;
    private final ScheduledExecutorService executor;

    @Inject
    public RequestStats()
    {
        executor = new ScheduledThreadPoolExecutor(2, new ThreadFactoryBuilder().setNameFormat("RequestStatsTicker-%s").setDaemon(true).build());

        request = new CounterStat(executor);
        schedulingTime = new TimeStat();
        requestTime = new TimeStat();
        responseTime = new TimeStat();
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
            Duration schedulingDelay,
            Duration requestProcessingTime,
            Duration responseProcessingTime)
    {
        request.update(1);
        schedulingTime.update((long) schedulingDelay.toMillis());
        requestTime.update((long) requestProcessingTime.toMillis());
        responseTime.update((long) responseProcessingTime.toMillis());
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
    public TimeStat getSchedulingTime()
    {
        return schedulingTime;
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
