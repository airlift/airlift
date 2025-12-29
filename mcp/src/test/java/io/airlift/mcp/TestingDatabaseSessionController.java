package io.airlift.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import io.airlift.concurrent.Threads;
import io.airlift.log.Logger;
import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.mcp.sessions.SessionConditionUtil.waitForCondition;
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
                created_at TIMESTAMP NOT NULL DEFAULT now()
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

    private static final String VALIDATE_SESSION_SQL = """
            SELECT 1 FROM sessions WHERE session_id = ?
            """;

    private static final String VALIDATE_SESSION_FOR_UPDATE_SQL = VALIDATE_SESSION_SQL + " FOR UPDATE";

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
            INSERT INTO sessions (session_id) VALUES (?)
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
    private final Cache<String, Semaphore> notificationSemaphores;

    @Inject
    public TestingDatabaseSessionController(TestingDatabaseServer database, ObjectMapper objectMapper)
    {
        this.database = requireNonNull(database, "database is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");

        executorService = Executors.newSingleThreadExecutor(Threads.daemonThreadsNamed("testing-session-controller-%s"));

        notificationSemaphores = CacheBuilder.newBuilder()
                .softValues()
                .build();
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
    public SessionId createSession(Optional<McpIdentity> identity, Optional<Duration> ttl)
    {
        // this is merely a test implementation, so we ignore the ttl

        SessionId sessionId = new SessionId(UUID.randomUUID().toString());

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(CREATE_SESSION_SQL);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.executeUpdate();
        });

        return sessionId;
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        return database.withConnection(connection ->
                internalValidateSession(connection, sessionId, VALIDATE_SESSION_SQL));
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_SESSION_SQL);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionValueKey<T> key)
    {
        return database.withConnection(connection ->
                        internalGetValue(connection, sessionId, key, SELECT_VALUE_SQL))
                .map(maybeJson -> mapJson(key.type(), maybeJson));
    }

    @Override
    public <T> boolean computeSessionValue(SessionId sessionId, SessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId, VALIDATE_SESSION_FOR_UPDATE_SQL)) {
                return false;
            }

            boolean hasRetried = false;
            boolean isDone = false;

            while (!isDone) {
                if (hasRetried) {
                    throw new RuntimeException("Failed to compute session value after retrying");
                }
                hasRetried = true;

                Optional<T> currentValue = internalGetValue(connection, sessionId, key, SELECT_VALUE_FOR_UPDATE_SQL)
                        .map(maybeJson -> mapJson(key.type(), maybeJson));

                Optional<T> newValue = updater.apply(currentValue);

                if (newValue.isPresent()) {
                    String newValueJson = objectMapper.writeValueAsString(newValue.get());

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

            return true;
        });
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionValueKey<T> key, T value)
    {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId, VALIDATE_SESSION_SQL)) {
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
        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, sessionId, VALIDATE_SESSION_SQL)) {
                return false;
            }

            internalDeleteValue(connection, sessionId, key);

            postNotification(connection, sessionId, key);

            return true;
        });
    }

    @Override
    public <T> Optional<Boolean> blockUntilCondition(SessionId sessionId, SessionValueKey<T> key, Duration timeout, Function<Optional<T>, Boolean> condition)
            throws InterruptedException
    {
        /*
            blockUntilCondition() is merely an optimization and does not need to be perfect nor exact.
         */

        String value = notificationValue(sessionId, key);
        boolean result = waitForCondition(this, sessionId, key, timeout, condition, maxWait -> {
            long maxWaitMs = maxWait.toMillis();
            while (maxWaitMs > 0) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                try {
                    Semaphore semaphore = notificationSemaphores.get(value, () -> new Semaphore(0));
                    if (semaphore.tryAcquire(maxWaitMs, MILLISECONDS)) {
                        return true;
                    }
                    maxWaitMs -= stopwatch.elapsed().toMillis();
                }
                catch (ExecutionException e) {
                    // should never happen
                    throw new UncheckedExecutionException(e);
                }
            }
            return false;
        });

        return Optional.of(result);
    }

    @Override
    public <T> List<Map.Entry<String, T>> listSessionValues(SessionId sessionId, Class<T> type, int pageSize, Optional<String> lastName)
    {
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

    private static boolean internalValidateSession(Connection connection, SessionId sessionId, String validateSessionSql)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(validateSessionSql);
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
                log.error("Error connecting/listening to database", e);
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
                Semaphore semaphore = notificationSemaphores.getIfPresent(notification.getParameter());
                if (semaphore != null) {
                    semaphore.release();
                }
            });
        }
    }

    private static String type(SessionValueKey<?> key)
    {
        return key.type().getName();
    }
}
