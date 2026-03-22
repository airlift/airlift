package io.airlift.mcp;

import com.google.inject.Inject;
import io.airlift.concurrent.Threads;
import io.airlift.log.Logger;
import io.airlift.mcp.storage.Signals;
import io.airlift.mcp.storage.Storage;
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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestingDatabaseStorage
        implements Storage
{
    private static final Logger log = Logger.get(TestingDatabaseStorage.class);

    private static final String CREATE_TABLES_SQL =
            """
            CREATE TABLE IF NOT EXISTS groups
            (
                group_name VARCHAR(36) PRIMARY KEY,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                ttl_ms BIGINT NULL
            );
            
            CREATE TABLE IF NOT EXISTS group_values
            (
                group_name VARCHAR(36) NOT NULL,
                key VARCHAR(255) NOT NULL,
                value JSONB NOT NULL,
                PRIMARY KEY (group_name, key),
                FOREIGN KEY (group_name) REFERENCES groups (group_name) ON DELETE CASCADE
            );
            """;

    private static final String DELETE_STALE_GROUPS_SQL =
            """
            DELETE FROM groups
            WHERE ttl_ms IS NOT NULL
              AND (created_at + (ttl_ms || ' milliseconds')::INTERVAL) < now()
            """;

    private static final String UPDATE_USAGE_SQL =
            """
            UPDATE groups
            SET updated_at = now()
            WHERE group_name = ?
            """;

    private static final String VALIDATE_GROUP_SQL =
            """
            SELECT 1 FROM groups WHERE group_name = ?
            """;

    private static final String DELETE_GROUP_SQL =
            """
            DELETE FROM groups WHERE group_name = ?
            """;

    private static final String SELECT_VALUE_SQL =
            """
            SELECT value FROM group_values WHERE group_name = ? AND key = ?
            """;

    private static final String SELECT_VALUE_FOR_UPDATE_SQL = SELECT_VALUE_SQL + " FOR UPDATE";

    private static final String SET_VALUE_SQL =
            """
            INSERT INTO group_values (group_name, key, value)
            SELECT ?, ?, ?::jsonb
            WHERE EXISTS (SELECT 1 FROM groups WHERE group_name = ?)
            ON CONFLICT (group_name, key) DO UPDATE
                SET value = EXCLUDED.value
            """;

    private static final String INSERT_VALUE_IF_NOT_EXISTS_SQL =
            """
            INSERT INTO group_values (group_name, key, value)
            VALUES (?, ?, ?::jsonb)
            ON CONFLICT DO NOTHING
            """;

    private static final String UPDATE_VALUE_SQL =
            """
            UPDATE group_values
            SET value = ?::jsonb
            WHERE group_name = ? AND key = ?
            """;

    private static final String DELETE_VALUE_SQL =
            """
            DELETE FROM group_values
            WHERE group_name = ? AND key = ?
            """;

    private static final String CREATE_GROUP_SQL =
            """
            INSERT INTO groups (group_name, ttl_ms) VALUES (?, ?)
            """;

    private static final String LIST_VALUES_SQL =
            """
            SELECT key
            FROM group_values
            WHERE group_name = ?
            ORDER BY key
            """;

    private static final String GET_GROUPS_SQL =
            """
            SELECT group_name FROM groups
            ORDER BY group_name;
            """;

    private static final String LISTEN_SQL = "LISTEN mcp_internal_channel";

    private static final String NOTIFY_SQL =
            """
            NOTIFY mcp_internal_channel, '%s'
            """;

    private final TestingDatabaseServer database;
    private final ExecutorService executorService;
    private final Signals signals;
    private final AtomicReference<Instant> lastStaleSessionCleanup = new AtomicReference<>(Instant.now());
    private final Duration sessionTimeout;

    @Inject
    public TestingDatabaseStorage(TestingDatabaseServer database, McpConfig mcpConfig)
    {
        this.database = requireNonNull(database, "database is null");

        executorService = Executors.newSingleThreadExecutor(Threads.daemonThreadsNamed("testing-session-controller-%s"));

        signals = new Signals();
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
        if (!shutdownAndAwaitTermination(executorService, 10, SECONDS)) {
            log.warn("Executor shutdown failed");
        }
    }

    @Override
    public void createGroup(String group, Duration ttl)
    {
        checkClean();

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(CREATE_GROUP_SQL);
            preparedStatement.setString(1, group);
            preparedStatement.setLong(2, ttl.toMillis());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public boolean groupExists(String group)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_USAGE_SQL);
            preparedStatement.setString(1, group);
            return preparedStatement.executeUpdate() != 0;
        });
    }

    @Override
    public void deleteGroup(String group)
    {
        checkClean();

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_GROUP_SQL);
            preparedStatement.setString(1, group);
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public Optional<String> getValue(String group, String key)
    {
        checkClean();

        return database.withConnection(connection ->
                internalGetValue(connection, group, key, SELECT_VALUE_SQL));
    }

    @Override
    public boolean setValue(String group, String key, String value)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, group)) {
                return false;
            }

            PreparedStatement preparedStatement = connection.prepareStatement(SET_VALUE_SQL);
            preparedStatement.setString(1, group);
            preparedStatement.setString(2, key);
            preparedStatement.setString(3, value);
            preparedStatement.setString(4, group);
            preparedStatement.executeUpdate();

            postNotification(connection, group);

            return true;
        });
    }

    @Override
    public void deleteValue(String group, String key)
    {
        checkClean();

        database.inTransaction(connection -> {
            if (!internalValidateSession(connection, group)) {
                return;
            }

            internalDeleteValue(connection, group, key);

            postNotification(connection, group);
        });
    }

    @Override
    public Optional<String> computeValue(String group, String key, UnaryOperator<Optional<String>> updater)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateSession(connection, group)) {
                return Optional.empty();
            }

            boolean hasRetried = false;
            boolean isDone = false;

            Optional<String> result = Optional.empty();

            while (!isDone) {
                Optional<String> currentValue = internalGetValue(connection, group, key, SELECT_VALUE_FOR_UPDATE_SQL);

                result = updater.apply(currentValue);

                if (result.isPresent()) {
                    if (currentValue.isEmpty()) {
                        PreparedStatement preparedStatement = connection.prepareStatement(INSERT_VALUE_IF_NOT_EXISTS_SQL);
                        preparedStatement.setString(1, group);
                        preparedStatement.setString(2, key);
                        preparedStatement.setString(3, result.get());
                        if (preparedStatement.executeUpdate() != 0) {
                            isDone = true;
                        }
                        // otherwise, another transaction inserted the value, so we need to retry
                    }
                    else if (!currentValue.get().equals(result.orElseThrow())) {
                        PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_VALUE_SQL);
                        preparedStatement.setString(1, result.get());
                        preparedStatement.setString(2, group);
                        preparedStatement.setString(3, key);
                        if (preparedStatement.executeUpdate() == 0) {
                            throw new RuntimeException("Failed to update existing session value");
                        }
                        isDone = true;
                    }
                }
                else {
                    internalDeleteValue(connection, group, key);
                    isDone = true;
                }

                if (!isDone && hasRetried) {
                    throw new RuntimeException("Failed to compute session value after retrying");
                }
                hasRetried = true;
            }

            postNotification(connection, group);

            return result;
        });
    }

    @Override
    public void waitForSignal(String group, Duration timeout)
            throws InterruptedException
    {
        signals.waitForSignal(group, timeout);
    }

    @Override
    public void signalAll(String group)
    {
        signals.signalAll(group);
    }

    @Override
    public Stream<String> groups()
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(GET_GROUPS_SQL);
            var resultSet = preparedStatement.executeQuery();

            List<String> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
            return results;
        }).stream();
    }

    @Override
    public Stream<String> keys(String group)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(LIST_VALUES_SQL);
            preparedStatement.setString(1, group);
            var resultSet = preparedStatement.executeQuery();

            List<String> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
            return results;
        }).stream();
    }

    private static void internalDeleteValue(Connection connection, String group, String key)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(DELETE_VALUE_SQL);
        preparedStatement.setString(1, group);
        preparedStatement.setString(2, key);
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
                    return statement.executeUpdate(DELETE_STALE_GROUPS_SQL);
                });

                if (qtyDeleted > 0) {
                    log.info("Cleaned up %d stale sessions".formatted(qtyDeleted));
                }
            }
        }
    }

    private void postNotification(Connection connection, String group)
            throws SQLException
    {
        connection.createStatement().execute(NOTIFY_SQL.formatted(group));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean internalValidateSession(Connection connection, String group)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(VALIDATE_GROUP_SQL);
        preparedStatement.setString(1, group);
        var resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }

    private static Optional<String> internalGetValue(Connection connection, String group, String key, String selectValueSql)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(selectValueSql);
        preparedStatement.setString(1, group);
        preparedStatement.setString(2, key);
        var resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            String value = resultSet.getString(1);
            return Optional.of(value);
        }
        return Optional.empty();
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
            PGNotification[] notifications = connection.unwrap(PgConnection.class).getNotifications(0);
            Stream.of(notifications).forEach(notification -> {
                signals.signalAll(notification.getParameter());
            });
        }
    }
}
