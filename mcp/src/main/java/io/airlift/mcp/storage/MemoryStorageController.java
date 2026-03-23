package io.airlift.mcp.storage;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class MemoryStorageController
        implements StorageController
{
    private static final Logger log = Logger.get(MemoryStorageController.class);

    private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Duration cleanupInterval;
    private final ConcurrentHashMap<StorageGroupId, Group> groups;
    private final AtomicReference<Instant> lastCleanup;
    private final Signals signals;

    private static class Group
    {
        private final Map<StorageKeyId, String> values = new ConcurrentHashMap<>();
        private final Duration ttl;
        private volatile Instant lastUsage = Instant.now();

        private Group(Duration ttl)
        {
            this.ttl = requireNonNull(ttl, "ttl is null");
        }

        private Optional<String> get(StorageKeyId key)
        {
            lastUsage = Instant.now();

            return Optional.ofNullable(values.get(key));
        }

        private void put(StorageKeyId key, String value)
        {
            lastUsage = Instant.now();
            values.put(key, value);
        }

        private Optional<String> compute(StorageKeyId key, UnaryOperator<Optional<String>> updater)
        {
            lastUsage = Instant.now();

            String computed = values.compute(key, (_, existingValue) -> {
                Optional<String> existing = Optional.ofNullable(existingValue);
                return updater.apply(existing).orElse(null);
            });

            return Optional.ofNullable(computed);
        }

        private boolean delete(StorageKeyId key)
        {
            lastUsage = Instant.now();

            return (values.remove(key) != null);
        }

        private boolean canBeExpired(Instant now)
        {
            return lastUsage.plus(ttl).isBefore(now);
        }
    }

    @Inject
    public MemoryStorageController()
    {
        this(DEFAULT_CLEANUP_INTERVAL);
    }

    public MemoryStorageController(Duration cleanupInterval)
    {
        this.cleanupInterval = requireNonNull(cleanupInterval, "cleanupInterval is null");
        groups = new ConcurrentHashMap<>();
        signals = new Signals();
        lastCleanup = new AtomicReference<>(Instant.now());
    }

    @Override
    public void createGroup(StorageGroupId groupId, Duration ttl)
    {
        clean();

        groups.put(groupId, new Group(ttl));
    }

    @Override
    public boolean validateGroup(StorageGroupId groupId)
    {
        clean();

        return groups.containsKey(groupId);
    }

    @Override
    public void deleteGroup(StorageGroupId groupId)
    {
        clean();

        groups.remove(groupId);
        signalGroup(groupId);
    }

    @Override
    public List<StorageGroupId> listGroups(Optional<StorageGroupId> cursor)
    {
        clean();

        if (cursor.isPresent()) {
            return ImmutableList.of();
        }

        return ImmutableList.copyOf(groups.keySet());
    }

    @Override
    public List<StorageKeyId> listGroupKeys(StorageGroupId groupId, Optional<StorageKeyId> cursor)
    {
        clean();

        cursor.ifPresent(_ -> {
            throw new UnsupportedOperationException("listGroups with cursor is not supported");
        });

        return Optional.ofNullable(groups.get(groupId))
                .map(group -> ImmutableList.copyOf(group.values.keySet()))
                .orElse(ImmutableList.of());
    }

    @Override
    public Optional<String> getValue(StorageGroupId groupId, StorageKeyId keyId)
    {
        clean();

        return Optional.ofNullable(groups.get(groupId))
                .flatMap(group -> group.get(keyId));
    }

    @Override
    public boolean setValue(StorageGroupId groupId, StorageKeyId keyId, String value)
    {
        clean();

        return Optional.ofNullable(groups.get(groupId))
                .map(group -> {
                    group.put(keyId, value);
                    signalGroup(groupId);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean deleteValue(StorageGroupId groupId, StorageKeyId keyId)
    {
        clean();

        return Optional.ofNullable(groups.get(groupId))
                .map(group -> {
                    boolean result = group.delete(keyId);
                    signalGroup(groupId);
                    return result;
                })
                .orElse(false);
    }

    @Override
    public Optional<String> computeValue(StorageGroupId groupId, StorageKeyId keyId, UnaryOperator<Optional<String>> updater)
    {
        clean();

        return Optional.ofNullable(groups.get(groupId))
                .flatMap(group -> {
                    Optional<String> computed = group.compute(keyId, updater);
                    signalGroup(groupId);
                    return computed;
                });
    }

    @Override
    public boolean await(StorageGroupId groupId, Duration timeout)
            throws InterruptedException
    {
        clean();

        return signals.waitForSignal(groupId.group(), timeout);
    }

    private void clean()
    {
        Instant now = Instant.now();
        Instant localLastCleanup = lastCleanup.get();
        Duration lastCleanupAge = Duration.between(localLastCleanup, now);

        if (lastCleanupAge.compareTo(cleanupInterval) >= 0) {
            if (lastCleanup.compareAndSet(localLastCleanup, now)) {
                for (Map.Entry<StorageGroupId, Group> entry : groups.entrySet()) {
                    if (entry.getValue().canBeExpired(now)) {
                        log.info("Cleaning up stale storage %s", entry.getKey());
                        groups.remove(entry.getKey());
                    }
                }
            }
        }
    }

    private void signalGroup(StorageGroupId groupId)
    {
        signals.signalAll(groupId.group());
    }
}
