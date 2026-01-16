package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import io.airlift.concurrent.Threads;
import io.airlift.log.Logger;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.sessions.Signal;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.mcp.sessions.SessionConditionUtil.waitForCondition;
import static java.sql.Types.BIGINT;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestingDatabaseSessionController
        implements SessionController
{
    private static final Logger log = Logger.get(TestingDatabaseSessionController.class);

    private static final int LISTEN_TIMEOUT_MS = 1000;

    private static final String CREATE_TABLES_SQL = """
            CREATE TABLE IF NOT EXISTS sessions
            (
                session_id VARCHAR(36) PRIMARY KEY,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                ttl_ms BIGINT NULL
            );
            
            CREATE TABLE IF NOT EXISTS session_values
            (
                session_id VARCHAR(36) NOT NULL,
                type VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                value JSONB NOT NULL,
                PRIMARY KEY (session_id, type, name),
                FOREIGN KEY (session_id) REFERENCES sessions (session_id) ON DELETE CASCADE
            );
            """;

    private static final String DELETE_STALE_SESSIONS_SQL = """
            DELETE FROM sessions
            WHERE ttl_ms IS NOT NULL
              AND (created_at + (ttl_ms || ' milliseconds')::INTERVAL) < now()
            """;

    private static final String UPDATE_SESSION_SQL = """
            UPDATE sessions
            SET updated_at = now()
            WHERE session_id = ?
            """;

    private static final String VALIDATE_SESSION_SQL = """
            SELECT 1 FROM sessions WHERE session_id = ?
            """;

    private static final String DELETE_SESSION_SQL = """
            DELETE FROM sessions WHERE session_id = ?
            """;

    private static final String SELECT_VALUE_SQL = """
            SELECT value FROM session_values WHERE session_id = ? AND type = ? AND name = ?
            """;

    private static final String SELECT_VALUE_FOR_UPDATE_SQL = SELECT_VALUE_SQL + " FOR UPDATE";

    private static final String SET_VALUE_SQL = """
            INSERT INTO session_values (session_id, type, name, value)
            SELECT ?, ?, ?, ?::jsonb
            WHERE EXISTS (SELECT 1 FROM sessions WHERE session_id = ?)
            ON CONFLICT (session_id, type, name) DO UPDATE
                SET value = EXCLUDED.value
            """;

    private static final String INSERT_VALUE_IF_NOT_EXISTS_SQL = """
            INSERT INTO session_values (session_id, type, name, value)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT DO NOTHING
            """;

    private static final String UPDATE_VALUE_SQL = """
            UPDATE session_values
            SET value = ?::jsonb
            WHERE session_id = ? AND type = ? AND name = ?
            """;

    private static final String DELETE_VALUE_SQL = """
            DELETE FROM session_values
            WHERE session_id = ? AND type = ? AND name = ?
            """;

    private static final String CREATE_SESSION_SQL = """
            INSERT INTO sessions (session_id, ttl_ms) VALUES (?, ?)
            """;

    private static final String LIST_VALUES_SQL = """
            SELECT name, value
            FROM session_values
            WHERE (session_id = ? AND type = ?)
              AND (name > ? OR ? IS NULL)
            ORDER BY name
            LIMIT ?
            """;

    private static final String GET_SESSIONS_SQL = """
            SELECT session_id FROM sessions
            """;

    private static final String LIST_SESSIONS_SQL = """
            SELECT session_id FROM sessions
            WHERE (session_id > ? OR ? IS NULL)
            ORDER BY session_id
            LIMIT ?
            """;

    private static final String LISTEN_SQL = "LISTEN mcp_internal_session_channel";

    private static final String NOTIFY_SQL = """
            NOTIFY mcp_internal_session_channel, '%s'
            """;

    private final TestingDatabaseServer database;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Cache<String, Signal> signals;
    private final AtomicReference<Instant> lastStaleSessionCleanup = new AtomicReference<>(Instant.now());
    private final Duration sessionTimeout;

    @Inject
    public TestingDatabaseSessionController(TestingDatabaseServer database, ObjectMapper objectMapper, McpConfig mcpConfig)
    {
        this.database = requireNonNull(database, "database is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        executorService = Executors.newSingleThreadExecutor(Threads.daemonThreadsNamed("testing-session-controller-%s"));

        signals = CacheBuilder.newBuilder()
                .softValues()
                .build();

        sessionTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();
    }

    @PostConstruct
    public void initialize()
    {
        database.inTransaction(connection -> connection.createStatement().execute(CREATE_TABLES_SQL));

        executorService.execute(this::listener);
    }

    @PreDestroy
    public void close()
    {
        if (!shutdownAndAwaitTermination(executorService, LISTEN_TIMEOUT_MS * 2, MILLISECONDS)) {
            log.warn("Executor shutdown failed");
        }
    }

    @Override
    public SessionId createSession(McpIdentity identity, Optional<Duration> ttl)
    {
        checkClean();

        SessionId sessionId = new SessionId(UUID.randomUUID().toString());

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(CREATE_SESSION_SQL);
            preparedStatement.setString(1, sessionId.id());
            if (ttl.isPresent()) {
                preparedStatement.setLong(2, ttl.get().toMillis());
            }
            else {
                preparedStatement.setNull(2, BIGINT);
            }
            preparedStatement.executeUpdate();
        });

        return sessionId;
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_SESSION_SQL);
            preparedStatement.setString(1, sessionId.id());
            return preparedStatement.executeUpdate() != 0;
        });
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        checkClean();

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_SESSION_SQL);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        checkClean();

        return database.withConnection(connection ->
                        internalGetValue(connection, sessionId, key, SELECT_VALUE_SQL))
                .map(maybeJson -> mapJson(key.type(), maybeJson));
    }

    @Override
    public <T> Optional<T> computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId)) {
                return Optional.empty();
            }

            boolean hasRetried = false;
            boolean isDone = false;

            Optional<T> result = Optional.empty();

            while (!isDone) {
                if (hasRetried) {
                    throw new RuntimeException("Failed to compute session value after retrying");
                }
                hasRetried = true;

                Optional<T> currentValue = internalGetValue(connection, sessionId, key, SELECT_VALUE_FOR_UPDATE_SQL)
                        .map(maybeJson -> mapJson(key.type(), maybeJson));

                result = updater.apply(currentValue);

                if (result.isPresent()) {
                    String newValueJson = objectMapper.writeValueAsString(result.get());

                    if (currentValue.isEmpty()) {
                        PreparedStatement preparedStatement = connection.prepareStatement(INSERT_VALUE_IF_NOT_EXISTS_SQL);
                        preparedStatement.setString(1, sessionId.id());
                        preparedStatement.setString(2, type(key));
                        preparedStatement.setString(3, key.name());
                        preparedStatement.setString(4, newValueJson);
                        if (preparedStatement.executeUpdate() != 0) {
                            isDone = true;
                        }
                        // otherwise, another transaction inserted the value, so we need to retry
                    }
                    else {
                        PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_VALUE_SQL);
                        preparedStatement.setString(1, newValueJson);
                        preparedStatement.setString(2, sessionId.id());
                        preparedStatement.setString(3, type(key));
                        preparedStatement.setString(4, key.name());
                        if (preparedStatement.executeUpdate() == 0) {
                            throw new RuntimeException("Failed to update existing session value");
                        }
                        isDone = true;
                    }
                }
                else {
                    internalDeleteValue(connection, sessionId, key);
                    isDone = true;
                }
            }

            postNotification(connection, sessionId, key);

            return result;
        });
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        checkClean();

        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId)) {
                return false;
            }

            PreparedStatement preparedStatement = connection.prepareStatement(SET_VALUE_SQL);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, type(key));
            preparedStatement.setString(3, key.name());
            preparedStatement.setString(4, json);
            preparedStatement.setString(5, sessionId.id());
            preparedStatement.executeUpdate();

            postNotification(connection, sessionId, key);

            return true;
        });
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId)) {
                return false;
            }

            internalDeleteValue(connection, sessionId, key);

            postNotification(connection, sessionId, key);

            return true;
        });
    }

    @Override
    public <T> void blockUntilCondition(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        checkClean();

        /*
            blockUntilCondition() is merely an optimization and does not need to be perfect nor exact.
         */

        String value = notificationValue(sessionId, key);
        waitForCondition(this, sessionId, key, timeout, condition, maxWait -> {
            try {
                Signal signal = signals.get(value, Signal::new);
                signal.waitForSignal(maxWait.toMillis(), MILLISECONDS);
            }
            catch (ExecutionException e) {
                // should never happen
                throw new UncheckedExecutionException(e);
            }
        });
    }

    @Override
    public <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> lastName)
    {
        checkClean();

        return database.withConnection(connection -> {
                    PreparedStatement preparedStatement = connection.prepareStatement(LIST_VALUES_SQL);
                    preparedStatement.setString(1, sessionId.id());
                    preparedStatement.setString(2, type.getName());
                    preparedStatement.setString(3, lastName.orElse(null));
                    preparedStatement.setString(4, lastName.orElse(null));
                    preparedStatement.setInt(5, pageSize);
                    var resultSet = preparedStatement.executeQuery();

                    ImmutableList.Builder<Map.Entry<String, String>> results = ImmutableList.builder();
                    while (resultSet.next()) {
                        String name = resultSet.getString(1);
                        String valueJson = resultSet.getString(2);
                        results.add(Map.entry(name, valueJson));
                    }
                    return results.build();
                })
                .stream()
                .map(entry -> Map.entry(entry.getKey(), mapJson(type, entry.getValue())))
                .collect(toImmutableList());
    }

    @Override
    public List<SessionId> listSessions(int pageSize, Optional<SessionId> cursor)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(LIST_SESSIONS_SQL);
            preparedStatement.setString(1, cursor.map(SessionId::id).orElse(null));
            preparedStatement.setString(2, cursor.map(SessionId::id).orElse(null));
            preparedStatement.setInt(3, pageSize);
            var resultSet = preparedStatement.executeQuery();

            List<SessionId> results = new ArrayList<>();
            while (resultSet.next()) {
                String id = resultSet.getString(1);
                results.add(new SessionId(id));
            }
            return results;
        });
    }

    @VisibleForTesting
    public Set<SessionId> sessionIds()
    {
        return database.withConnection(connection -> {
            Statement statement = connection.createStatement();
            var resultSet = statement.executeQuery(GET_SESSIONS_SQL);

            ImmutableSet.Builder<SessionId> builder = ImmutableSet.builder();
            while (resultSet.next()) {
                String sessionId = resultSet.getString(1);
                builder.add(new SessionId(sessionId));
            }
            return builder.build();
        });
    }

    private void postNotification(Connection connection, SessionId sessionId, SessionValueKey<?> key)
            throws SQLException
    {
        String value = notificationValue(sessionId, key);
        connection.createStatement().execute(NOTIFY_SQL.formatted(value));
    }

    private static String notificationValue(SessionId sessionId, SessionValueKey<?> key)
    {
        String value = sessionId.id() + "|" + key.name() + "|" + type(key);
        return value.replace("'", "");
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean internalValidateSession(Connection connection, SessionId sessionId)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(VALIDATE_SESSION_SQL);
        preparedStatement.setString(1, sessionId.id());
        var resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }

    private static <T> Optional<String> internalGetValue(Connection connection, SessionId sessionId, SessionValueKey<T> key, String selectValueSql)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(selectValueSql);
        preparedStatement.setString(1, sessionId.id());
        preparedStatement.setString(2, type(key));
        preparedStatement.setString(3, key.name());
        var resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            String valueJson = resultSet.getString(1);
            return Optional.of(valueJson);
        }
        return Optional.empty();
    }

    private static <T> void internalDeleteValue(Connection connection, SessionId sessionId, SessionValueKey<T> key)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(DELETE_VALUE_SQL);
        preparedStatement.setString(1, sessionId.id());
        preparedStatement.setString(2, type(key));
        preparedStatement.setString(3, key.name());
        preparedStatement.executeUpdate();
    }

    private void checkClean()
    {
        Instant lastCleanup = lastStaleSessionCleanup.get();
        Instant now = Instant.now();
        if (Duration.between(lastCleanup, now).compareTo(sessionTimeout) >= 0) {
            if (lastStaleSessionCleanup.compareAndSet(lastCleanup, now)) {
                int qtyDeleted = database.withTransaction(connection -> {
                    Statement statement = connection.createStatement();
                    return statement.executeUpdate(DELETE_STALE_SESSIONS_SQL);
                });

                if (qtyDeleted > 0) {
                    log.info("Cleaned up %d stale sessions".formatted(qtyDeleted));
                }
            }
        }
    }

    private <T> T mapJson(Class<T> type, String json)
    {
        try {
            return objectMapper.readValue(json, type);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void listener()
    {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.info("Starting database listener for session notifications");
                try (Connection connection = database.newRawConnection()) {
                    listener(connection);
                }
            }
            catch (Throwable e) {
                log.error(e, "Error connecting/listening to database");
                try {
                    SECONDS.sleep(1);
                }
                catch (InterruptedException _) {
                    log.info("Interrupted while waiting for database connection - exiting listener thread");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void listener(Connection connection)
            throws SQLException
    {
        connection.createStatement().execute(LISTEN_SQL);

        while (!Thread.currentThread().isInterrupted()) {
            PGNotification[] notifications = connection.unwrap(PgConnection.class).getNotifications(LISTEN_TIMEOUT_MS);
            Stream.of(notifications).forEach(notification -> {
                Signal signal = signals.getIfPresent(notification.getParameter());
                if (signal != null) {
                    signal.signalAll();
                }
            });
        }
    }

    private static String type(SessionValueKey<?> key)
    {
        return key.type().getName();
    }
}
