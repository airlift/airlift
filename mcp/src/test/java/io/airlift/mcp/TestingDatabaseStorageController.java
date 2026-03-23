package io.airlift.mcp;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.storage.Signals;
import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.storage.StorageGroupId;
import io.airlift.mcp.storage.StorageKeyId;
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
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestingDatabaseStorageController
        implements StorageController
{
    private static final Logger log = Logger.get(TestingDatabaseStorageController.class);

    private static final int PAGE_SIZE = 100;

    private static final String CREATE_TABLES_SQL =
            """
            CREATE TABLE IF NOT EXISTS groups
            (
                group_id VARCHAR(36) PRIMARY KEY,
                created_at TIMESTAMP NOT NULL DEFAULT now(),
                updated_at TIMESTAMP NOT NULL DEFAULT now(),
                ttl_ms BIGINT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS group_values
            (
                group_id VARCHAR(36) NOT NULL,
                key_id VARCHAR(255) NOT NULL,
                value JSONB NOT NULL,
                PRIMARY KEY (group_id, key_id),
                FOREIGN KEY (group_id) REFERENCES groups (group_id) ON DELETE CASCADE
            );
            """;

    private static final String DELETE_STALE_GROUPS_SQL =
            """
            DELETE FROM groups
            WHERE (created_at + (ttl_ms || ' milliseconds')::INTERVAL) < now()
            """;

    private static final String UPDATE_GROUP_SQL =
            """
            UPDATE groups
            SET updated_at = now()
            WHERE group_id = ?
            """;

    private static final String VALIDATE_GROUP_SQL =
            """
            SELECT 1 FROM groups WHERE group_id = ?
            """;

    private static final String DELETE_GROUP_SQL =
            """
            DELETE FROM groups WHERE group_id = ?
            """;

    private static final String SELECT_VALUE_SQL =
            """
            SELECT value FROM group_values WHERE group_id = ? AND key_id = ?
            """;

    private static final String SELECT_VALUE_FOR_UPDATE_SQL = SELECT_VALUE_SQL + " FOR UPDATE";

    private static final String SET_VALUE_SQL =
            """
            INSERT INTO group_values (group_id, key_id, value)
            SELECT ?, ?, ?::jsonb
            WHERE EXISTS (SELECT 1 FROM groups WHERE group_id = ?)
            ON CONFLICT (group_id, key_id) DO UPDATE
                SET value = EXCLUDED.value
            """;

    private static final String INSERT_VALUE_IF_NOT_EXISTS_SQL =
            """
            INSERT INTO group_values (group_id, key_id, value)
            VALUES (?, ?, ?::jsonb)
            ON CONFLICT DO NOTHING
            """;

    private static final String UPDATE_VALUE_SQL =
            """
            UPDATE group_values
            SET value = ?::jsonb
            WHERE group_id = ? AND key_id = ?
            """;

    private static final String DELETE_VALUE_SQL =
            """
            DELETE FROM group_values
            WHERE group_id = ? AND key_id = ?
            """;

    private static final String CREATE_GROUP_SQL =
            """
            INSERT INTO groups (group_id, ttl_ms) VALUES (?, ?)
            """;

    private static final String LIST_GROUPS_SQL =
            """
            SELECT group_id FROM groups
            WHERE (group_id > ? OR ? IS NULL)
            ORDER BY group_id
            LIMIT ?
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
    public TestingDatabaseStorageController(TestingDatabaseServer database, McpConfig mcpConfig)
    {
        this.database = requireNonNull(database, "database is null");

        executorService = Executors.newThreadPerTaskExecutor(virtualThreadsNamed("TestingDatabaseStorageController-%d"));
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
    public void createGroup(StorageGroupId groupId, Duration ttl)
    {
        checkClean();

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(CREATE_GROUP_SQL);
            preparedStatement.setString(1, groupId.group());
            preparedStatement.setLong(2, ttl.toMillis());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public boolean validateGroup(StorageGroupId groupId)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_GROUP_SQL);
            preparedStatement.setString(1, groupId.group());
            return preparedStatement.executeUpdate() != 0;
        });
    }

    @Override
    public void deleteGroup(StorageGroupId groupId)
    {
        checkClean();

        database.inTransaction(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(DELETE_GROUP_SQL);
            preparedStatement.setString(1, groupId.group());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public List<StorageGroupId> listGroups(Optional<StorageGroupId> cursor)
    {
        checkClean();

        return database.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(LIST_GROUPS_SQL);
            preparedStatement.setString(1, cursor.map(StorageGroupId::group).orElse(null));
            preparedStatement.setString(2, cursor.map(StorageGroupId::group).orElse(null));
            preparedStatement.setInt(3, PAGE_SIZE);
            var resultSet = preparedStatement.executeQuery();

            List<StorageGroupId> results = new ArrayList<>();
            while (resultSet.next()) {
                String id = resultSet.getString(1);
                results.add(new StorageGroupId(id));
            }
            return results;
        });
    }

    @Override
    public Optional<String> getValue(StorageGroupId groupId, StorageKeyId keyId)
    {
        checkClean();

        return database.withConnection(connection -> internalGetValue(connection, groupId, keyId, SELECT_VALUE_SQL));
    }

    @Override
    public boolean setValue(StorageGroupId groupId, StorageKeyId keyId, String value)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateGroup(connection, groupId)) {
                return false;
            }

            PreparedStatement preparedStatement = connection.prepareStatement(SET_VALUE_SQL);
            preparedStatement.setString(1, groupId.group());
            preparedStatement.setString(2, keyId.key());
            preparedStatement.setString(3, value);
            preparedStatement.setString(4, groupId.group());
            preparedStatement.executeUpdate();

            postNotification(connection, groupId);

            return true;
        });
    }

    @Override
    public boolean deleteValue(StorageGroupId groupId, StorageKeyId keyId)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateGroup(connection, groupId)) {
                return false;
            }

            internalDeleteValue(connection, groupId, keyId);

            postNotification(connection, groupId);

            return true;
        });
    }

    @Override
    public Optional<String> computeValue(StorageGroupId groupId, StorageKeyId keyId, UnaryOperator<Optional<String>> updater)
    {
        checkClean();

        return database.withTransaction(connection -> {
            if (!internalValidateGroup(connection, groupId)) {
                return Optional.empty();
            }

            boolean hasRetried = false;
            boolean isDone = false;

            Optional<String> result = Optional.empty();

            while (!isDone) {
                Optional<String> currentValue = internalGetValue(connection, groupId, keyId, SELECT_VALUE_FOR_UPDATE_SQL);

                result = updater.apply(currentValue);

                if (result.isPresent()) {
                    if (currentValue.isEmpty()) {
                        PreparedStatement preparedStatement = connection.prepareStatement(INSERT_VALUE_IF_NOT_EXISTS_SQL);
                        preparedStatement.setString(1, groupId.group());
                        preparedStatement.setString(2, keyId.key());
                        preparedStatement.setString(3, result.orElseThrow());
                        if (preparedStatement.executeUpdate() != 0) {
                            isDone = true;
                        }
                        // otherwise, another transaction inserted the value, so we need to retry
                    }
                    else if (!currentValue.equals(result)) {
                        PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_VALUE_SQL);
                        preparedStatement.setString(1, result.orElseThrow());
                        preparedStatement.setString(2, groupId.group());
                        preparedStatement.setString(3, keyId.key());
                        if (preparedStatement.executeUpdate() == 0) {
                            throw new RuntimeException("Failed to update existing session value");
                        }
                        isDone = true;
                    }
                }
                else {
                    internalDeleteValue(connection, groupId, keyId);
                    isDone = true;
                }

                if (!isDone && hasRetried) {
                    throw new RuntimeException("Failed to compute group value after retrying");
                }
                hasRetried = true;
            }

            postNotification(connection, groupId);

            return result;
        });
    }

    @Override
    public boolean await(StorageGroupId groupId, Duration timeout)
            throws InterruptedException
    {
        return signals.waitForSignal(groupId.group(), timeout);
    }

    private static void internalDeleteValue(Connection connection, StorageGroupId groupId, StorageKeyId keyId)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(DELETE_VALUE_SQL);
        preparedStatement.setString(1, groupId.group());
        preparedStatement.setString(2, keyId.key());
        preparedStatement.executeUpdate();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean internalValidateGroup(Connection connection, StorageGroupId groupId)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(VALIDATE_GROUP_SQL);
        preparedStatement.setString(1, groupId.group());
        var resultSet = preparedStatement.executeQuery();
        return resultSet.next();
    }

    private static Optional<String> internalGetValue(Connection connection, StorageGroupId groupId, StorageKeyId keyId, String selectValueSql)
            throws SQLException
    {
        PreparedStatement preparedStatement = connection.prepareStatement(selectValueSql);
        preparedStatement.setString(1, groupId.group());
        preparedStatement.setString(2, keyId.key());
        var resultSet = preparedStatement.executeQuery();
        if (resultSet.next()) {
            String valueJson = resultSet.getString(1);
            return Optional.of(valueJson);
        }
        return Optional.empty();
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

    private void listener()
    {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.info("Starting database listener for notifications");
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
            Stream.of(notifications).forEach(notification -> signals.signalAll(notification.getParameter()));
        }
    }

    private void postNotification(Connection connection, StorageGroupId groupId)
            throws SQLException
    {
        connection.createStatement().execute(NOTIFY_SQL.formatted(groupId.group()));
    }
}
