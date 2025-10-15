package io.airlift.http.server.jetty;

import static java.util.Objects.requireNonNull;

import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.weakref.jmx.Managed;

public class MonitoredQueuedThreadPoolMBean {
    private final MonitoredQueuedThreadPool threadPool;

    public MonitoredQueuedThreadPoolMBean(MonitoredQueuedThreadPool threadPool) {
        this.threadPool = requireNonNull(threadPool, "threadPool is null");
    }

    @Managed(description = "maximum time a thread may be idle in ms")
    public int getIdleTimeout() {
        return threadPool.getIdleTimeout();
    }

    @Managed(description = "maximum number of threads in the pool")
    public int getMaxThreads() {
        return threadPool.getMaxThreads();
    }

    @Managed(description = "minimum number of threads in the pool")
    public int getMinThreads() {
        return threadPool.getMinThreads();
    }

    @Managed(description = "name of the thread pool")
    public String getName() {
        return threadPool.getName();
    }

    @Managed(description = "priority of the threads in the pool")
    public int getPriority() {
        return threadPool.getThreadsPriority();
    }

    @Managed(description = "size of the job queue")
    public int getQueueSize() {
        return threadPool.getQueueSize();
    }

    @Managed(description = "threshold at which the pool is low on threads")
    public int getLowThreadsThreshold() {
        return threadPool.getLowThreadsThreshold();
    }

    @Managed(description = "number of threads in the pool")
    public int getThreads() {
        return threadPool.getThreads();
    }

    @Managed(description = "number of idle threads in the pool")
    public int getIdleThreads() {
        return threadPool.getIdleThreads();
    }

    @Managed(description = "number of busy threads in the pool")
    public int getBusyThreads() {
        return threadPool.getBusyThreads();
    }

    @Managed(description = "whether thread pool is low on threads")
    public boolean isLowOnThreads() {
        return threadPool.isLowOnThreads();
    }

    @Managed(description = "the number of tasks executed")
    public long getTasks() {
        return threadPool.getTasks();
    }

    @Managed(description = "the maximum number of busy threads")
    public int getMaxBusyThreads() {
        return threadPool.getMaxBusyThreads();
    }

    @Managed(description = "the maximum task queue size")
    public int getMaxQueueSize() {
        return threadPool.getMaxQueueSize();
    }

    @Managed(description = "the average time a task remains in the queue, in nanoseconds")
    public long getAverageQueueLatency() {
        return threadPool.getAverageQueueLatency();
    }

    @Managed(description = "the maximum time a task remains in the queue, in nanoseconds")
    public long getMaxQueueLatency() {
        return threadPool.getMaxQueueLatency();
    }

    @Managed(description = "the average task execution time, in nanoseconds")
    public long getAverageTaskLatency() {
        return threadPool.getAverageTaskLatency();
    }

    @Managed(description = "the maximum task execution time, in nanoseconds")
    public long getMaxTaskLatency() {
        return threadPool.getMaxTaskLatency();
    }
}
