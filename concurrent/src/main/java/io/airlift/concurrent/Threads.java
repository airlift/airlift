package io.airlift.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.airlift.log.Logger;

import java.util.concurrent.ThreadFactory;

public final class Threads
{
    private static final Logger log = Logger.get(Threads.class);

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
        GroupedThreadFactory delegate = new GroupedThreadFactory(threadGroupName(nameFormat));
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setThreadFactory(new ContextClassLoaderThreadFactory(Thread.currentThread().getContextClassLoader(), delegate))
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
        GroupedThreadFactory delegate = new GroupedThreadFactory(threadGroupName(nameFormat));
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
                .setDaemon(true)
                .setThreadFactory(new ContextClassLoaderThreadFactory(Thread.currentThread().getContextClassLoader(), delegate))
                .build();
    }

    private static String threadGroupName(String nameFormat)
    {
        String groupFormat = nameFormat.replace("%d", "%s");
        if (!nameFormat.equals(groupFormat)) {
            log.warn("Invalid thread group nameFormat: %s", nameFormat);
        }
        return String.format(groupFormat, "group");
    }

    private static class ContextClassLoaderThreadFactory
            implements ThreadFactory
    {
        private final ClassLoader classLoader;
        private final ThreadFactory delegate;

        public ContextClassLoaderThreadFactory(ClassLoader classLoader, ThreadFactory delegate)
        {
            this.classLoader = classLoader;
            this.delegate = delegate;
        }

        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = delegate.newThread(runnable);
            thread.setContextClassLoader(classLoader);
            return thread;
        }
    }

    private static final class GroupedThreadFactory
            implements ThreadFactory
    {
        private final ThreadGroup threadGroup;

        public GroupedThreadFactory(String name)
        {
            this.threadGroup =  new ThreadGroup(name);
        }

        @Override
        public Thread newThread(Runnable runnable)
        {
            return new Thread(threadGroup, runnable);
        }

        @Override
        protected void finalize()
        {
            try {
                threadGroup.destroy();
            }
            catch (RuntimeException e) {
                log.warn(e, "Leaked thread group '%s'", threadGroup.getName());
            }
        }
    }
}
