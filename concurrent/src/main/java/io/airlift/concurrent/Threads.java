package io.airlift.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ThreadFactory;

public final class Threads
{
    private Threads() {}

    /**
     * Creates a {@link ThreadFactory} that creates named threads
     * using the specified naming format.
     *
     * @param nameFormat a {@link String#format(String, Object...)}-compatible
     * format string, to which a unique integer will be supplied as the single
     * parameter. This integer will be unique to this instance of the
     * ThreadFactory and will be assigned sequentially.
     * @return the created ThreadFactory
     */
    public static ThreadFactory threadsNamed(String nameFormat)
    {
        return new ThreadFactoryBuilder()
                .setNameFormat(nameFormat)
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
                .build();
    }
}
