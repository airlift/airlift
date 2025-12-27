package io.airlift.mcp.sessions;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public interface SessionController
{
    /**
     * Create a new session and return its ID
     */
    SessionId createSession(HttpServletRequest request);

    /**
     * @return {@code true} if the session ID is valid, {@code false} otherwise
     */
    boolean validateSession(SessionId sessionId);

    /**
     * Delete the session or do nothing if the session ID is invalid
     */
    void deleteSession(SessionId sessionId);

    /**
     * @return the value associated with the given key in the session, or {@link Optional#empty()}
     * if the key is not present in the session or if the session ID is invalid
     */
    <T> Optional<T> getSessionValue(SessionId sessionId, ValueKey<T> key);

    /**
     * Set the value associated with the given key in the session
     *
     * @return {@code true} if the value was set, {@code false} if the session ID is invalid
     */
    <T> boolean setSessionValue(SessionId sessionId, ValueKey<T> key, T value);

    /**
     * <p>
     *     Modify the value associated with the given key in the session using the provided updater function. This operation
     *     is intended to be atomic if possible. It should roughly correspond to {@link java.util.concurrent.ConcurrentHashMap#compute(Object, BiFunction)}
     * </p>
     *
     * <p>
     *     {@code updater} receives an {@link Optional} containing the existing value (or {@link Optional#empty()} if no value is present)
     *     and should return an {@link Optional} containing the new value to set (or {@link Optional#empty()} to remove the value).
     * </p>
     *
     * @return {@code true} if the value was updated, {@code false} if the session ID is invalid
     */
    <T> boolean computeSessionValue(SessionId sessionId, ValueKey<T> key, UnaryOperator<Optional<T>> updater);

    /**
     * Delete the value associated with the given key in the session
     *
     * @return {@code true} if the value was deleted, {@code false} if the session ID is invalid
     */
    <T> boolean deleteSessionValue(SessionId sessionId, ValueKey<T> key);

    /**
     * @return the value associated with the given key from the global system, or {@link Optional#empty()}
     * if the key is not present. Values in the global system never expire.
     */
    <T> Optional<T> getSystemValue(ValueKey<T> key);

    /**
     * <p>
     *     Modify the value associated with the given key in the global system using the provided updater function. This operation
     *     is intended to be atomic if possible. It should roughly correspond to {@link java.util.concurrent.ConcurrentHashMap#compute(Object, BiFunction)}
     * </p>
     *
     * <p>
     *     {@code updater} receives an {@link Optional} containing the existing value (or {@link Optional#empty()} if no value is present)
     *     and should return an {@link Optional} containing the new value to set (or {@link Optional#empty()} to remove the value).
     * </p>
     *
     * <p>
     *      Values in the global system never expire.
     * </p>
     */
    <T> void computeSystemValue(ValueKey<T> key, UnaryOperator<Optional<T>> updater);
}
