package io.airlift.mcp.legacy.sessions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import io.airlift.log.Logger;
import io.airlift.mcp.storage.Storage;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.airlift.mcp.legacy.sessions.LegacyBlockingResult.fulfilled;
import static io.airlift.mcp.legacy.sessions.LegacyBlockingResult.timedOut;
import static java.util.Objects.requireNonNull;

class LegacySessionImpl
        implements LegacySession
{
    private static final Logger log = Logger.get(LegacySessionImpl.class);

    private final LegacySessionId sessionId;
    private final Storage storage;
    private final JsonMapper jsonMapper;

    LegacySessionImpl(LegacySessionId sessionId, Storage storage, JsonMapper jsonMapper)
    {
        this.sessionId = requireNonNull(sessionId, "sessionId is null");
        this.storage = requireNonNull(storage, "storage is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @Override
    public LegacySessionId sessionId()
    {
        return sessionId;
    }

    @Override
    public boolean isValid()
    {
        return storage.groupExists(sessionId.id());
    }

    @Override
    public <T> Optional<T> getValue(LegacySessionValueKey<T> key)
    {
        return storage.getValue(sessionId.id(), toKey(key))
                .flatMap(json -> deserialize(key, json));
    }

    @Override
    public <T> void setValue(LegacySessionValueKey<T> key, T value)
    {
        storage.setValue(sessionId.id(), toKey(key), serialize(value));
    }

    @Override
    public <T> void deleteValue(LegacySessionValueKey<T> key)
    {
        storage.deleteValue(sessionId.id(), toKey(key));
    }

    @Override
    public <T> Optional<T> computeValue(LegacySessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        Optional<String> computedValue = storage.computeValue(sessionId.id(), toKey(key), json -> {
            Optional<T> currentValue = json.flatMap(jsonValue -> deserialize(key, jsonValue));
            Optional<T> newValue = updater.apply(currentValue);
            return newValue.map(this::serialize);
        });

        return computedValue.flatMap(json -> deserialize(key, json));
    }

    @Override
    public <T> LegacyBlockingResult<T> blockUntil(LegacySessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        long timeoutMsRemaining = timeout.toMillis();
        while (true) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Optional<T> value = getValue(key);
            if (condition.test(value)) {
                return fulfilled(value);
            }

            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            if (timeoutMsRemaining <= 0) {
                return timedOut(timeout);
            }

            stopwatch.reset().start();
            storage.waitForSignal(sessionId.id(), Duration.ofMillis(timeoutMsRemaining));
            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
        }
    }

    @Override
    public void signalAll()
    {
        storage.signalAll(sessionId.id());
    }

    @VisibleForTesting
    <T> Optional<T> deserialize(LegacySessionValueKey<T> key, String json)
    {
        try {
            return Optional.of(jsonMapper.readValue(json, key.type()));
        }
        catch (Exception e) {
            log.warn(e, "Failed to deserialize session value for key: %s, json: %s", key, json);
        }

        // if it's invalid just treat it like it's missing to avoid upgrade/compatiblity issues
        return Optional.empty();
    }

    private static String toKey(LegacySessionValueKey<?> key)
    {
        return key.type().getName() + "|" + key.name();
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
}
