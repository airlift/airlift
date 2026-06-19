package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public final class StatsBackendFactory
{
    public static final String STATS_BACKEND_PROPERTY = "io.airlift.stats.backend";

    private static final Object lock = new Object();

    @GuardedBy("lock")
    private static StatsBackend backend;
    @GuardedBy("lock")
    private static boolean frozen;

    private StatsBackendFactory() {}

    public static StatsBackend getBackend()
    {
        synchronized (lock) {
            if (backend == null) {
                backend = StatsBackend.fromPropertyValue(System.getProperty(STATS_BACKEND_PROPERTY, "airlift"));
            }
            frozen = true;
            return backend;
        }
    }

    public static void setBackend(StatsBackend backend)
    {
        requireNonNull(backend, "backend is null");
        synchronized (lock) {
            checkState(!frozen, "stats backend is already initialized");
            StatsBackendFactory.backend = backend;
        }
    }

    @VisibleForTesting
    static void resetForTesting()
    {
        synchronized (lock) {
            backend = null;
            frozen = false;
        }
    }
}
