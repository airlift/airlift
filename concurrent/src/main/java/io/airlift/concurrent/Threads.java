package io.airlift.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.Executors.defaultThreadFactory;

public final class Threads
{
    private Threads() {}

    /**
     * Creates a {@link ThreadFactory} that creates named threads
     * using the specified naming format.
     *
     * @param nameFormat a {@link String#format(String, Object...)}-compatible
     * format string, to which a string will be supplied as the single
     * parameter. This string will be unique to this instance of the
     * ThreadFactory and will be assigned sequentially.
     * @return the created ThreadFactory
     */
    public static ThreadFactory threadsNamed(String nameFormat)
    {
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setThreadFactory(new ContextClassLoaderThreadFactory(Thread.currentThread().getContextClassLoader(), defaultThreadFactory()))
                .build();
    }

    /**
     * Creates a {@link ThreadFactory} that creates named daemon threads.
     * using the specified naming format.
     *
     * @param nameFormat see {@link #threadsNamed(String)}
     * @return the created ThreadFactory
     */
    public static ThreadFactory daemonThreadsNamed(String nameFormat)
    {
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setDaemon(true)
                .setThreadFactory(new ContextClassLoaderThreadFactory(Thread.currentThread().getContextClassLoader(), defaultThreadFactory()))
                .build();
    }

    private record ContextClassLoaderThreadFactory(ClassLoader classLoader, ThreadFactory delegate)
            implements ThreadFactory
    {
        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = delegate.newThread(runnable);
            thread.setContextClassLoader(classLoader);
            return thread;
        }
    }
}
