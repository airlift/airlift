package io.airlift.mcp.session.memory;

import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class MemorySession
{
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final ReentrantLock waitLock;
    private final Condition waitCondition;

    public MemorySession()
    {
        waitLock = new ReentrantLock();
        waitCondition = waitLock.newCondition();
    }

    public void setValue(String key, Object value)
    {
        waitLock.lock();
        try {
            values.put(key, value);

            waitCondition.signalAll();
        }
        finally {
            waitLock.unlock();
        }
    }

    public Optional<Object> value(String key)
    {
        return Optional.ofNullable(values.get(key));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public <T> Optional<T> waitValueCondition(Supplier<Optional<T>> conditionSupplier, Duration timeout)
    {
        Optional<T> result = Optional.empty();
        Duration remaining = Duration.from(timeout);

        waitLock.lock();
        try {
            while (remaining.isPositive() && result.isEmpty()) {
                Stopwatch stopwatch = Stopwatch.createStarted();

                result = conditionSupplier.get();
                if (result.isEmpty()) {
                    try {
                        waitCondition.await(timeout.toMillis(), MILLISECONDS);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                remaining = remaining.minus(stopwatch.elapsed());
            }
        }
        finally {
            waitLock.unlock();
        }

        return result;
    }

    public Collection<String> currentKeys()
    {
        return values.keySet();
    }

    public void deleteValue(String key)
    {
        waitLock.lock();
        try {
            values.remove(key);

            waitCondition.signalAll();
        }
        finally {
            waitLock.unlock();
        }
    }
}
