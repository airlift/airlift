package io.airlift.mcp.legacy.sessions;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface LegacySession
{
    LegacySessionId sessionId();

    boolean isValid();

    <T> Optional<T> getValue(LegacySessionValueKey<T> key);

    <T> void setValue(LegacySessionValueKey<T> key, T value);

    <T> void deleteValue(LegacySessionValueKey<T> key);

    <T> Optional<T> computeValue(LegacySessionValueKey<T> key, UnaryOperator<Optional<T>> updater);

    <T> LegacyBlockingResult<T> blockUntil(LegacySessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException;

    void signalAll();
}
