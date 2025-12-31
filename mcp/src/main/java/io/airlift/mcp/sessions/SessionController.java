package io.airlift.mcp.sessions;

import io.airlift.mcp.McpIdentity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface SessionController
{
    Optional<Duration> FOREVER_TTL = Optional.empty();

    /**
     * <p>
     * Create a new session and return its ID. {@code ttl} specifies the
     * suggested time-to-live duration for the session, the implementation may
     * choose to ignore it or use a different value.
     * </p>
     *
     * <p>
     * <b>If {@code ttl} is {@code empty()} (or {@code FOREVER_TTL}), the implementation must never expire
     * the session automatically</b>. Sessions created with {@code empty()} TTLs
     * are specially intended for system or admin use cases where the session
     * must persist until explicitly deleted.
     * </p>
     */
    SessionId createSession(Optional<McpIdentity> identity, Optional<Duration> ttl);

    default SessionId createPermanentSession(Optional<McpIdentity> identity)
    {
        return createSession(identity, FOREVER_TTL);
    }

    /**
     * @return {@code true} if the session ID is valid, {@code false} otherwise
     */
    boolean validateSession(SessionId sessionId);

    /**
     * Delete the session or do nothing if the session ID is invalid
     */
    void deleteSession(SessionId sessionId);

    /**
     * <p>
     *     Block until the given condition returns {@code true} for the value associated with the given key in the session,
     *     or until the timeout expires.
     * </p>
     *
     * <p>
     *     IMPORTANT: this is optional behavior. If the implementation does not support waiting for session changes,
     *     this method should immediately {@code empty()}. It is used internally merely as an optimization to attempt
     *     to get quick notice of key changes without polling. However, polling will always still be used so this method,
     *     if implemented, does not need to be perfect.
     * </p>
     *
     * <p>
     *     The condition receives an optional value for the key. If the value is {@code empty()}, the key is not present in the session,
     *     has been deleted, etc.
     * </p>
     *
     * @return {@code true} if a change occurred, {@code false} if the timeout expired without any change,
     * if the implementation does not support blocking, or if the session id is invalid
     */
    <T> Optional<Boolean> blockUntilCondition(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Function<Optional<T>, Boolean> condition)
            throws InterruptedException;

    /**
     * @return the value associated with the given key in the session, or {@link Optional#empty()}
     * if the key is not present in the session or if the session ID is invalid
     */
    <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key);

    /**
     * <p>
     * Set the value associated with the given key in the session.
     * </p>
     *
     * @return {@code true} if the value was set, {@code false} if the session ID is invalid
     */
    <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value);

    /**
     * <p>
     * Modify the value associated with the given key in the session using the provided updater function. This operation
     * is intended to be atomic if possible. It should roughly correspond to {@link java.util.concurrent.ConcurrentHashMap#compute(Object, BiFunction)}
     * </p>
     *
     * <p>
     * {@code updater} receives an {@link Optional} containing the existing value (or {@link Optional#empty()} if no value is present)
     * and should return an {@link Optional} containing the new value to set (or {@link Optional#empty()} to remove the value).
     * </p>
     *
     * @return {@code true} if the value was updated, {@code false} if the session ID is invalid
     */
    <T> boolean computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater);

    /**
     * <p>
     * Delete the value associated with the given key in the session
     * </p>
     *
     * @return {@code true} if the value was deleted, {@code false} if the session ID is invalid
     */
    <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key);

    /**
     * <p>
     * List all values of the given type, up to the given page size, starting after the given last name (if present).
     * The values are returned in ascending order by key name. If {@code cursor} is empty, the listing starts from the beginning.
     * </p>
     *
     * <p>
     * Each value return is an entry of the key name and the value.
     * </p>
     */
    <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> cursor);

    /**
     * List all active sessions (except the system session), up to the given page size, starting after the given cursor (if present).
     * The sessions are returned in ascending order by session ID. If {@code cursor} is empty, the listing starts from the beginning.
     */
    List<SessionId> listSessions(int pageSize, Optional<SessionId> cursor);
}
