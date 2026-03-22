package io.airlift.mcp.storage;

import io.airlift.log.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class MemoryStorage
        implements Storage
{
    private static final Logger log = Logger.get(MemoryStorage.class);

    private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Signals signals;
    private final Map<String, Group> groups;
    private final AtomicReference<Duration> cleanupInterval;
    private final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.now());

    private record Group(Map<String, String> values, AtomicReference<Instant> lastUsage, Duration ttl)
    {
        private Group
        {
            requireNonNull(values, "value is null");    // do not copy
            requireNonNull(lastUsage, "lastUsage is null");
            requireNonNull(ttl, "ttl is null");
        }

        private void updateLastUsage()
        {
            lastUsage.set(Instant.now());
        }

        private boolean canBeExpired(Instant now)
        {
            return lastUsage.get().plus(ttl).isBefore(now);
        }
    }

    public MemoryStorage()
    {
        groups = new ConcurrentHashMap<>();
        signals = new Signals();
        cleanupInterval = new AtomicReference<>(DEFAULT_CLEANUP_INTERVAL);
    }

    @Override
    public void createGroup(String group, Duration ttl)
    {
        clean();

        Duration updatedCleanupInterval = Duration.ofMillis(Math.max(1, ttl.toMillis() / 2));
        cleanupInterval.updateAndGet(existing -> existing.compareTo(updatedCleanupInterval) > 0 ? updatedCleanupInterval : existing);

        if (groups.put(group, new Group(new ConcurrentHashMap<>(), new AtomicReference<>(Instant.now()), ttl)) != null) {
            throw new IllegalStateException("Duplicate group name: " + group);
        }
    }

    @Override
    public void deleteGroup(String group)
    {
        clean();

        groups.remove(group);
    }

    @Override
    public boolean groupExists(String group)
    {
        clean();

        return Optional.ofNullable(groups.get(group))
                .map(entry -> {
                    entry.updateLastUsage();
                    return true;
                })
                .orElse(false);
    }

    @Override
    public Optional<String> getValue(String group, String key)
    {
        clean();

        return Optional.ofNullable(groups.get(group))
                .flatMap(entry -> {
                    entry.updateLastUsage();
                    return Optional.ofNullable(entry.values().get(key));
                });
    }

    @Override
    public boolean setValue(String group, String key, String value)
    {
        clean();

        return Optional.ofNullable(groups.get(group))
                .map(entry -> {
                    entry.updateLastUsage();
                    entry.values().put(key, value);
                    signals.signalAll(group);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void deleteValue(String group, String key)
    {
        clean();

        Optional.ofNullable(groups.get(group))
                .ifPresent(entry -> {
                    entry.values().remove(key);
                    signals.signalAll(group);
                });
    }

    @Override
    public Optional<String> computeValue(String group, String key, UnaryOperator<Optional<String>> updater)
    {
        clean();

        return Optional.ofNullable(groups.get(group))
                .flatMap(entry -> {
                    String computedValue = entry.values.compute(key, (_, existingValue) -> {
                        entry.updateLastUsage();
                        Optional<String> newValue = updater.apply(Optional.ofNullable(existingValue));
                        return newValue.orElse(null);
                    });
                    signals.signalAll(group);
                    return Optional.ofNullable(computedValue);
                });
    }

    @Override
    public void waitForSignal(String group, Duration timeout)
            throws InterruptedException
    {
        signals.waitForSignal(group, timeout);
    }

    @Override
    public void signalAll(String group)
    {
        signals.signalAll(group);
    }

    @Override
    public Stream<String> groups()
    {
        return groups.keySet().stream();
    }

    @Override
    public Stream<String> keys(String group)
    {
        return Optional.ofNullable(groups.get(group))
                .stream()
                .flatMap(entry -> entry.values().keySet().stream());
    }

    private void clean()
    {
        Instant now = Instant.now();
        Instant localLastCleanup = lastCleanup.get();
        Duration lastCleanupAge = Duration.between(localLastCleanup, now);

        if (lastCleanupAge.compareTo(cleanupInterval.get()) >= 0) {
            if (lastCleanup.compareAndSet(localLastCleanup, now)) {
                for (Map.Entry<String, Group> entry : groups.entrySet()) {
                    if (entry.getValue().canBeExpired(now)) {
                        log.info("Cleaning up stale session %s", entry.getKey());
                        groups.remove(entry.getKey());
                    }
                }
            }
        }
    }
}
