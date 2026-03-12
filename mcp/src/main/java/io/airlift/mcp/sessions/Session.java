package io.airlift.mcp.sessions;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface Session
{
    SessionId NULL_SESSION_ID = new SessionId("");

    /**
     * @return the session ID or {@code NULL_SESSION_ID} if sessions aren't enabled
     */
    SessionId sessionId();

    /**
     * @return {@code true} if the session is valid, {@code false} otherwise. Important,
     *         if sessions are not enabled in the server this method also returns {@code false}. Always
     *         check this method before using the session object to avoid exceptions.
     */
    boolean isValid();

    /**
     * <p>
     * Block until the given condition returns {@code true} for the value associated with the given key in the session,
     * or until the timeout expires.
     * </p>
     *
     * <p>
     * IMPORTANT: this is optional behavior. If the implementation does not support waiting for session changes,
     * use the default implementation.
     * </p>
     *
     * <p>
     * The condition receives an optional value for the key. If the value is {@code empty()}, the key is not present in the session,
     * has been deleted, etc.
     * </p>
     */
    <T> BlockingResult<T> blockUntil(SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException;

    /**
     * @return the value associated with the given key in the session, or {@link Optional#empty()}
     *         if the key is not present in the session or if the session is invalid
     */
    <T> Optional<T> getValue(SessionValueKey<T> key);

    /**
     * <p>
     *     Set the value associated with the given key in the session.
     * </p>
     *
     * @return {@code true} if the value was set, {@code false} if the session is invalid
     */
    <T> boolean setValue(SessionValueKey<T> key, T value);

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
     * @return the computed value or {@code empty()} if that's the computed value or if the session is invalid
     */
    <T> Optional<T> computeValue(SessionValueKey<T> key, UnaryOperator<Optional<T>> updater);

    /**
     * <p>
     *     Delete the value associated with the given key in the session
     * </p>
     *
     * @return {@code true} if the value was deleted, {@code false} if the session is invalid
     */
    <T> boolean deleteValue(SessionValueKey<T> key);
}
