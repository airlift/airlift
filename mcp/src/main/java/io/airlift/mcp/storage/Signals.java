package io.airlift.mcp.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class Signals
{
    private final Cache<String, Signal> signals;

    public Signals()
    {
        signals = CacheBuilder.newBuilder()
                .softValues()
                .build();
    }

    void signalAll(String key)
    {
        Signal signal = signals.getIfPresent(key);
        if (signal != null) {
            signal.signalAll();
        }
    }

    void waitForSignal(String key, Duration timeout)
            throws InterruptedException
    {
        try {
            Signal signal = signals.get(key, Signal::new);
            signal.waitForSignal(timeout.toMillis(), MILLISECONDS);
        }
        catch (ExecutionException e) {
            // should never happen
            throw new UncheckedExecutionException(e);
        }
    }
}
