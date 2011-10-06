package com.proofpoint.http.server;

import com.proofpoint.units.Duration;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

import java.util.concurrent.TimeUnit;

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
        Duration requestTime = new Duration(System.currentTimeMillis() - request.getTimeStamp(), TimeUnit.MILLISECONDS);

        long dispatchTime = request.getDispatchTime();
        if (dispatchTime == 0) {
            dispatchTime = request.getTimeStamp();
        }

        Duration schedulingDelay = new Duration(dispatchTime - request.getTimeStamp(), TimeUnit.MILLISECONDS);

        stats.record(request.getMethod(), response.getStatus(), request.getContentRead(), response.getContentCount(), schedulingDelay, requestTime);
    }

    @Override
    public void start()
            throws Exception
    {
    }

    @Override
    public void stop()
            throws Exception
    {
    }

    @Override
    public boolean isRunning()
    {
        return true;
    }

    @Override
    public boolean isStarted()
    {
        return true;
    }

    @Override
    public boolean isStarting()
    {
        return false;
    }

    @Override
    public boolean isStopping()
    {
        return false;
    }

    @Override
    public boolean isStopped()
    {
        return false;
    }

    @Override
    public boolean isFailed()
    {
        return false;
    }

    @Override
    public void addLifeCycleListener(Listener listener)
    {
    }

    @Override
    public void removeLifeCycleListener(Listener listener)
    {
    }
}
