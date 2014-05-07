package io.airlift.concurrent;

import com.google.common.annotations.Beta;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@Beta
public class ThreadPoolExecutorMBean
{
    private final ThreadPoolExecutor threadPoolExecutor;

    public ThreadPoolExecutorMBean(ThreadPoolExecutor threadPoolExecutor)
    {
        this.threadPoolExecutor = checkNotNull(threadPoolExecutor, "threadPoolExecutor is null");
    }

    @Managed
    public boolean isShutdown()
    {
        return threadPoolExecutor.isShutdown();
    }

    @Managed
    public boolean isTerminating()
    {
        return threadPoolExecutor.isTerminating();
    }

    @Managed
    public boolean isTerminated()
    {
        return threadPoolExecutor.isTerminated();
    }

    @Managed
    public String getRejectedExecutionHandler()
    {
        return threadPoolExecutor.getRejectedExecutionHandler().getClass().getName();
    }

    @Managed
    public int getCorePoolSize()
    {
        return threadPoolExecutor.getCorePoolSize();
    }

    @Managed
    public void setCorePoolSize(int corePoolSize)
    {
        threadPoolExecutor.setCorePoolSize(corePoolSize);
    }

    @Managed
    public int getMaximumPoolSize()
    {
        return threadPoolExecutor.getMaximumPoolSize();
    }

    @Managed
    public void setMaximumPoolSize(int maximumPoolSize)
    {
        threadPoolExecutor.setMaximumPoolSize(maximumPoolSize);
    }

    @Managed
    public int getPoolSize()
    {
        return threadPoolExecutor.getPoolSize();
    }

    @Managed
    public int getActiveCount()
    {
        return threadPoolExecutor.getActiveCount();
    }

    @Managed
    public int getLargestPoolSize()
    {
        return threadPoolExecutor.getLargestPoolSize();
    }

    @Managed
    public String getKeepAliveTime()
    {
        return new Duration(threadPoolExecutor.getKeepAliveTime(NANOSECONDS), NANOSECONDS)
                .convertToMostSuccinctTimeUnit()
                .toString();
    }

    @Managed
    public void setKeepAliveTime(String duration)
    {
        checkNotNull(duration, "duration is null");
        threadPoolExecutor.setKeepAliveTime(Duration.valueOf(duration).roundTo(NANOSECONDS), NANOSECONDS);
    }

    @Managed
    public boolean isAllowCoreThreadTimeOut()
    {
        return threadPoolExecutor.allowsCoreThreadTimeOut();
    }

    @Managed
    public void setAllowCoreThreadTimeOut(boolean allowsCoreThreadTimeOut)
    {
        threadPoolExecutor.allowCoreThreadTimeOut(allowsCoreThreadTimeOut);
    }

    @Managed
    public long getTaskCount()
    {
        return threadPoolExecutor.getTaskCount();
    }

    @Managed
    public long getCompletedTaskCount()
    {
        return threadPoolExecutor.getCompletedTaskCount();
    }

    @Managed
    public int getQueuedTaskCount()
    {
        return threadPoolExecutor.getQueue().size();
    }
}
