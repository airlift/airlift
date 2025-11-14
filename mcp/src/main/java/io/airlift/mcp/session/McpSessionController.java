package io.airlift.mcp.session;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public interface McpSessionController
{
    /**
     * Creates a new session and returns its session ID.
     */
    String newSession();

    /**
     * Returns the set of currently active session IDs.
     */
    Set<String> currentSessionIds();

    /**
     * Deletes the session with the given session ID.
     *
     * @return true if the session was deleted, false if the session did not exist.
     */
    boolean deleteSession(String sessionId);

    /**
     * @return true if the session exists, false if the session does not exist.
     */
    boolean isValidSession(String sessionId);

    /**
     * Upserts the given value for the given key in the session.
     *
     * @return true if the session exists, false if the session does not exist.
     */
    <T> boolean upsertValue(String sessionId, McpValueKey<T> key, T value);

    /**
     * Deletes the value for the given key in the session.
     *
     * @return true if the session exists, false if the session does not exist.
     */
    <T> boolean deleteValue(String sessionId, McpValueKey<T> key);

    /**
     * Gets the current value for the given key in the session.
     *
     * @return an Optional containing the current value, or an empty Optional if the value is not set or the session does not exist.
     */
    default <T> Optional<T> currentValue(String sessionId, McpValueKey<T> key)
    {
        return Optional.ofNullable(currentValue(sessionId, key, null));
    }

    /**
     * @return the current value for the given key in the session, or returns the default value if the value is not set or the session does not exist.
     */
    <T> T currentValue(String sessionId, McpValueKey<T> key, T defaultValue);

    /**
     * Waits until the {@code conditionSupplier} returns a non-empty Optional, the timeout is reached, or the thread is interrupted.
     * The {@code conditionSupplier} is polled when the method is called and then whenever any value in the session is updated.
     *
     * @return the value from the {@code conditionSupplier} if non-empty, otherwise an empty Optional if the timeout is reached or if the thread is interrupted.
     */
    <T> Optional<T> waitValueCondition(String sessionId, Supplier<Optional<T>> conditionSupplier, Duration timeout);

    /**
     * @return the collection of all keys with current values in the session, or an empty collection if the session does not exist or has no values.
     */
    Collection<String> currentValueKeys(String sessionId);
}
