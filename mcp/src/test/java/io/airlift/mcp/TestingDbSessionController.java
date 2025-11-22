package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionKey;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class TestingDbSessionController
        implements SessionController
{
    private static final String KEY_TYPE_SEPARATOR = ",";

    private final TestingDatabaseServer databaseServer;
    private final ObjectMapper objectMapper;

    @Inject
    public TestingDbSessionController(TestingDatabaseServer databaseServer, ObjectMapper objectMapper)
    {
        this.databaseServer = requireNonNull(databaseServer, "databaseServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    public static void createSchema(TestingDatabaseServer databaseServer)
    {
        databaseServer.inConnection(connection -> {
            Statement statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE sessions (
                        session_id VARCHAR(128) PRIMARY KEY
                    );

                    CREATE TABLE session_values (
                        session_value_key VARCHAR(256) NOT NULL,
                        session_id VARCHAR(128) references sessions(session_id) ON DELETE CASCADE,
                        value_json TEXT NOT NULL,
                        PRIMARY KEY (session_id, session_value_key)
                    );
                    """);
        });
    }

    public static void truncate(TestingDatabaseServer databaseServer)
    {
        databaseServer.inConnection(connection -> {
            try {
                connection.setAutoCommit(false);
                Statement statement = connection.createStatement();
                statement.execute("DELETE FROM sessions");
                connection.commit();
            }
            catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        });
    }

    @Override
    public SessionId createSession(HttpServletRequest request)
    {
        SessionId sessionId = new SessionId(UUID.randomUUID().toString());
        databaseServer.inConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    INSERT INTO sessions (session_id)
                    VALUES (?)
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.executeUpdate();
        });
        return sessionId;
    }

    @Override
    public boolean validateSession(SessionId sessionId)
    {
        return databaseServer.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT count(*) FROM sessions WHERE session_id = ?
                    """);
            preparedStatement.setString(1, sessionId.id());
            var resultSet = preparedStatement.executeQuery();
            resultSet.next();
            return resultSet.getInt(1) > 0;
        });
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        databaseServer.inConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    DELETE FROM sessions WHERE session_id = ?
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.executeUpdate();
        });
    }

    @Override
    public <T> Optional<T> getSessionValue(SessionId sessionId, SessionKey<T> key)
    {
        return databaseServer.withConnection(connection -> internalGetSessionValue(connection, sessionId, key, false));
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionKey<T> key, T value)
    {
        return databaseServer.withConnection(connection -> internalSetSessionValue(connection, sessionId, key, value));
    }

    @Override
    public <T> boolean computeSessionValue(SessionId sessionId, SessionKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return databaseServer.withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                Optional<T> currentValue = internalGetSessionValue(connection, sessionId, key, true);
                Optional<T> newValue = updater.apply(currentValue);
                boolean result = newValue.map(v -> internalSetSessionValue(connection, sessionId, key, v))
                        .orElseGet(() -> internalDeleteSessionValue(connection, sessionId, key));
                connection.commit();
                return result;
            }
            catch (Exception e) {
                try {
                    connection.rollback();
                }
                catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        });
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionKey<T> key)
    {
        return databaseServer.withConnection(connection -> internalDeleteSessionValue(connection, sessionId, key));
    }

    @Override
    public boolean deleteSessionValues(SessionId sessionId, Collection<? extends SessionKey<?>> keys)
    {
        return databaseServer.withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                boolean result = false;
                for (SessionKey<?> key : keys) {
                    result |= internalDeleteSessionValue(connection, sessionId, key);
                }
                connection.commit();
                return result;
            }
            catch (Exception e) {
                try {
                    connection.rollback();
                }
                catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw e;
            }
        });
    }

    private String keyToId(SessionKey<?> sessionKey)
    {
        return sessionKey.name() + KEY_TYPE_SEPARATOR + sessionKey.type().getName();
    }

    private <T> boolean internalDeleteSessionValue(Connection connection, SessionId sessionId, SessionKey<T> key)
    {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    DELETE FROM session_values
                    WHERE (session_id = ?) AND (session_value_key = ?)
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, keyToId(key));
            return preparedStatement.executeUpdate() > 0;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> boolean internalSetSessionValue(Connection connection, SessionId sessionId, SessionKey<T> key, T value)
    {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    INSERT INTO session_values (session_id, session_value_key, value_json)
                    VALUES (?, ?, ?)
                    ON CONFLICT (session_id, session_value_key) DO
                    UPDATE SET value_json = EXCLUDED.value_json
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, keyToId(key));
            preparedStatement.setString(3, objectMapper.writeValueAsString(value));
            return preparedStatement.executeUpdate() > 0;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Optional<T> internalGetSessionValue(Connection connection, SessionId sessionId, SessionKey<T> key, boolean forUpdate)
    {
        try {
            String query = """
                    SELECT value_json
                    FROM session_values
                    WHERE (session_id = ?) AND (session_value_key = ?)
                    """;
            if (!forUpdate) {
                query += " FOR UPDATE";
            }
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, keyToId(key));
            var resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String valueJson = resultSet.getString(1);
                T value = objectMapper.readerFor(key.type()).readValue(valueJson);
                return Optional.ofNullable(value);
            }
            return Optional.empty();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
