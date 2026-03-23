package io.airlift.mcp.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class Signals
{
    private final Cache<String, Signal> signals;

    public Signals()
    {
        signals = CacheBuilder.newBuilder()
                .softValues()
                .build();
    }

    public boolean waitForSignal(String signalName, Duration timeout)
            throws InterruptedException
    {
        Signal signal;
        try {
            signal = signals.get(signalName, Signal::new);
        }
        catch (ExecutionException e) {
            // should never happen
            throw new UncheckedExecutionException(e);
        }

        return signal.waitForSignal(timeout);
    }

    public void signalAll(String signalName)
    {
        Signal signal = signals.getIfPresent(signalName);
        if (signal != null) {
            signal.signalAll();
        }
    }
}
