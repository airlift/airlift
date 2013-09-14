package io.airlift.http.client.jetty;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.Closeable;
import java.util.concurrent.Executor;

public class JettyIoPool
        implements Closeable
{
    private final String name;
    private final QueuedThreadPool executor;
    private final ByteBufferPool byteBufferPool;
    private final Scheduler scheduler;

    public JettyIoPool(String name, JettyIoPoolConfig config)
    {
        this.name = name;
        try {
            QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads(), config.getMinThreads());
            threadPool.setName("http-client-" + name);
            threadPool.setDaemon(true);
            threadPool.start();
            threadPool.setStopTimeout(2000);
            executor = threadPool;

            scheduler = new ScheduledExecutorScheduler("http-client-" + name + "-scheduler", true);
            scheduler.start();

            byteBufferPool = new MappedByteBufferPool();
        }
        catch (Exception e) {
            close();
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void close()
    {
        try {
            closeQuietly(executor);
        }
        finally {
            closeQuietly(scheduler);
        }
    }

    private static void closeQuietly(LifeCycle service)
    {
        try {
            if (service != null) {
                service.stop();
            }
        }
        catch (Exception ignored) {
        }
    }

    public String getName()
    {
        return name;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public ByteBufferPool setByteBufferPool()
    {
        return byteBufferPool;
    }

    public Scheduler setScheduler()
    {
        return scheduler;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("name", name)
                .toString();
    }
}
