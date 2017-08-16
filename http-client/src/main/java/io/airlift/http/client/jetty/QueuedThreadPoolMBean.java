package io.airlift.http.client.jetty;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.weakref.jmx.Managed;

import static java.util.Objects.requireNonNull;

public class QueuedThreadPoolMBean
{
    private final QueuedThreadPool threadPool;

    public QueuedThreadPoolMBean(QueuedThreadPool threadPool)
    {
        this.threadPool = requireNonNull(threadPool, "threadPool is null");
    }

    @Managed(description = "maximum time a thread may be idle in ms")
    public int getIdleTimeout()
    {
        return threadPool.getIdleTimeout();
    }

    @Managed(description = "maximum number of threads in the pool")
    public int getMaxThreads()
    {
        return threadPool.getMaxThreads();
    }

    @Managed(description = "minimum number of threads in the pool")
    public int getMinThreads()
    {
        return threadPool.getMinThreads();
    }

    @Managed(description = "name of the thread pool")
    public String getName()
    {
        return threadPool.getName();
    }

    @Managed(description = "priority of the threads in the pool")
    public int getPriority()
    {
        return threadPool.getThreadsPriority();
    }

    @Managed(description = "size of the job queue")
    public int getQueueSize()
    {
        return threadPool.getQueueSize();
    }

    @Managed(description = "threshold at which the pool is low on threads")
    public int getLowThreadsThreshold()
    {
        return threadPool.getLowThreadsThreshold();
    }

    @Managed(description = "number of threads in the pool")
    public int getThreads()
    {
        return threadPool.getThreads();
    }

    @Managed(description = "number of idle threads in the pool")
    public int getIdleThreads()
    {
        return threadPool.getIdleThreads();
    }

    @Managed(description = "number of busy threads in the pool")
    public int getBusyThreads()
    {
        return threadPool.getBusyThreads();
    }

    @Managed(description = "whether thread pool is low on threads")
    public boolean isLowOnThreads()
    {
        return threadPool.isLowOnThreads();
    }
}
