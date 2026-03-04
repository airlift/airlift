package io.airlift.mcp.sessions;

import io.airlift.mcp.McpIdentity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.airlift.mcp.sessions.SessionConditionUtil.waitForCondition;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public interface SessionController
{
    Optional<Duration> FOREVER_TTL = Optional.empty();

    ///
    ///     Create a new session and return its ID. `ttl` specifies the
    ///     suggested time-to-live duration for the session, the implementation may
    ///     choose to ignore it or use a different value.
    ///
    ///
    ///     **If `ttl` is `empty()` (or `FOREVER_TTL`), the implementation must never expire
    ///     the session automatically**. Sessions created with `empty()` TTLs
    ///     are specially intended for system or admin use cases where the session
    ///     must persist until explicitly deleted.
    ///
    SessionId createSession(McpIdentity identity, Optional<Duration> ttl);

    /// @return `true` if the session ID is valid, `false` otherwise
    boolean validateSession(SessionId sessionId);

    /// Delete the session or do nothing if the session ID is invalid
    void deleteSession(SessionId sessionId);

    ///
    /// Block until the given condition returns `true` for the value associated with the given key in the session,
    /// or until the timeout expires.
    ///
    ///
    /// IMPORTANT: this is optional behavior. If the implementation does not support waiting for session changes,
    /// use the default implementation.
    ///
    ///
    /// The condition receives an optional value for the key. If the value is `empty()`, the key is not present in the session,
    /// has been deleted, etc.
    ///
    default <T> BlockingResult<T> blockUntil(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        return waitForCondition(this, sessionId, key, timeout, condition, maxWait -> MILLISECONDS.sleep(maxWait.toMillis()));
    }

    /// @return the value associated with the given key in the session, or [Optional#empty()]
    /// if the key is not present in the session or if the session ID is invalid
    <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key);

    ///
    ///     Set the value associated with the given key in the session.
    ///
    ///
    /// @return `true` if the value was set, `false` if the session ID is invalid
    <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value);

    ///
    ///     Modify the value associated with the given key in the session using the provided updater function. This operation
    ///     is intended to be atomic if possible. It should roughly correspond to [java.util.concurrent.ConcurrentHashMap#compute(Object, BiFunction)]
    ///
    ///
    ///     `updater` receives an [Optional] containing the existing value (or [Optional#empty()] if no value is present)
    ///     and should return an [Optional] containing the new value to set (or [Optional#empty()] to remove the value).
    ///
    ///
    /// @return the computed value or `empty()` if that's the computed value or if the session ID is invalid
    <T> Optional<T> computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater);

    ///
    ///     Delete the value associated with the given key in the session
    ///
    ///
    /// @return `true` if the value was deleted, `false` if the session ID is invalid
    <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key);

    ///
    ///     List all values of the given type, up to the given page size, starting after the given last name (if present).
    ///     The values are returned in ascending order by key name. If `cursor` is empty, the listing starts from the beginning.
    ///
    ///
    ///     Each value return is an entry of the key name and the value.
    ///
    <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> cursor);

    /// List all active sessions (except the system session), up to the given page size, starting after the given cursor (if present).
    /// The sessions are returned in ascending order by session ID. If `cursor` is empty, the listing starts from the beginning.
    List<SessionId> listSessions(int pageSize, Optional<SessionId> cursor);
}
