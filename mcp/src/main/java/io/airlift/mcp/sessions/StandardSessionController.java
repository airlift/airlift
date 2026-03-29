package io.airlift.mcp.sessions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.storage.StorageGroupId;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.mcp.sessions.SessionConditionUtil.waitForCondition;
import static java.util.Objects.requireNonNull;

public class StandardSessionController
        implements SessionController
{
    private static final Logger log = Logger.get(StandardSessionController.class);

    private final StorageController storageController;
    private final Duration defaultTimeout;
    private final JsonMapper jsonMapper;

    @Inject
    public StandardSessionController(McpConfig mcpConfig, StorageController storageController, JsonMapper jsonMapper)
    {
        this.storageController = requireNonNull(storageController, "storageController is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");

        defaultTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();
    }

    @Override
    public SessionId createSession(McpIdentity identity, Optional<Duration> ttl)
    {
        StorageGroupId storageGroupId = storageController.randomGroupId();
        storageController.createGroup(storageGroupId, ttl.orElse(defaultTimeout));
        return new SessionId(storageGroupId.group());
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        return storageController.validateGroup(toGroupId(sessionId));
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        storageController.deleteGroup(toGroupId(sessionId));
    }

    @Override
    public <T> BlockingResult<T> blockUntil(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        return waitForCondition(this, sessionId, key, timeout, condition, maxWait -> storageController.await(toGroupId(sessionId), key.toKeyId(), maxWait));
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        return storageController.getValue(toGroupId(sessionId), key.toKeyId())
                .flatMap(value -> deserialize(key.type(), value));
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        return storageController.setValue(toGroupId(sessionId), key.toKeyId(), serialize(value));
    }

    @Override
    public <T> Optional<T> computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        var holder = new Object()
        {
            Optional<T> updated = Optional.empty();
        };
        storageController.computeValue(toGroupId(sessionId), key.toKeyId(), existing -> {
            Optional<T> deserialized = existing.flatMap(value -> deserialize(key.type(), value));
            holder.updated = updater.apply(deserialized);
            return holder.updated.map(this::serialize);
        });

        return holder.updated;
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        return storageController.deleteValue(toGroupId(sessionId), key.toKeyId());
    }

    @VisibleForTesting
    public Set<SessionId> sessionIds()
    {
        return storageController.listGroups(Optional.empty())
                .stream()
                .map(groupId -> new SessionId(groupId.group()))
                .collect(toImmutableSet());
    }

    private <T> String serialize(T value)
    {
        try {
            return jsonMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> Optional<T> deserialize(Class<T> type, String value)
    {
        try {
            return Optional.of(jsonMapper.readValue(value, type));
        }
        catch (JsonProcessingException e) {
            log.warn(e, "Failed to deserialize session value: %s", value);
        }
        // treat it as an old format value and return empty, which will cause the value to be overwritten on the next update
        return Optional.empty();
    }

    private StorageGroupId toGroupId(SessionId sessionId)
    {
        return new StorageGroupId(sessionId.id());
    }
}
