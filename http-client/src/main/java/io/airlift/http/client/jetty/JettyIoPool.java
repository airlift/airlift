package io.airlift.http.client.jetty;

import com.google.common.base.Throwables;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.Closeable;
import java.util.concurrent.Executor;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.lang.Thread.currentThread;

public final class JettyIoPool
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
            String baseName = "http-client-" + name;

            ThreadGroup threadGroup = new ThreadGroup(baseName);
            QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads(), config.getMinThreads(), 60000, null, threadGroup);
            threadPool.setName(baseName);
            threadPool.setDaemon(true);
            threadPool.start();
            threadPool.setStopTimeout(2000);
            executor = threadPool;

            scheduler = new ScheduledExecutorScheduler(baseName + "-scheduler", true, currentThread().getContextClassLoader(), threadGroup);
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

    public ByteBufferPool getByteBufferPool()
    {
        return byteBufferPool;
    }

    public Scheduler getScheduler()
    {
        return scheduler;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .toString();
    }
}
