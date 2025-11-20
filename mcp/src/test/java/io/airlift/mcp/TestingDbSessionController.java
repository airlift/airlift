package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionKey;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class TestingDbSessionController
        implements SessionController
{
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
                        session_key_name VARCHAR(256) NOT NULL,
                        session_key_type VARCHAR(256) NOT NULL,
                        session_id VARCHAR(128) references sessions(session_id) ON DELETE CASCADE,
                        value_json TEXT NOT NULL,
                        PRIMARY KEY (session_id, session_key_name, session_key_type)
                    );
                    """);
        });
    }

    public static void truncate(TestingDatabaseServer databaseServer)
    {
        databaseServer.inConnection(connection -> {
            Statement statement = connection.createStatement();
            statement.execute("DELETE FROM sessions");
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
            ResultSet resultSet = preparedStatement.executeQuery();
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
        return databaseServer.withConnection(connection -> internalGetSessionValue(connection, sessionId, key));
    }

    @Override
    public <T> boolean setSessionValue(SessionId sessionId, SessionKey<T> key, T value)
    {
        return databaseServer.withConnection(connection -> internalSetSessionValue(connection, sessionId, key, value));
    }

    @Override
    public <T> boolean computeSessionValue(SessionId sessionId, SessionKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        return databaseServer.withTransaction(connection -> {
            Optional<T> currentValue = internalGetSessionValue(connection, sessionId, key);
            Optional<T> newValue = updater.apply(currentValue);
            return newValue.map(v -> internalSetSessionValue(connection, sessionId, key, v))
                    .orElseGet(() -> internalDeleteSessionValue(connection, sessionId, key));
        });
    }

    @Override
    public <T> boolean deleteSessionValue(SessionId sessionId, SessionKey<T> key)
    {
        return databaseServer.withConnection(connection -> internalDeleteSessionValue(connection, sessionId, key));
    }

    private <T> boolean internalDeleteSessionValue(Connection connection, SessionId sessionId, SessionKey<T> key)
    {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    DELETE FROM session_values
                    WHERE (session_id = ?) AND (session_key_name = ?) AND (session_key_type = ?)
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, key.name());
            preparedStatement.setString(3, key.type().getName());
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
                    INSERT INTO session_values (session_id, session_key_name, session_key_type, value_json)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (session_id, session_key_name, session_key_type) DO
                    UPDATE SET value_json = EXCLUDED.value_json
                    """);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, key.name());
            preparedStatement.setString(3, key.type().getName());
            preparedStatement.setString(4, objectMapper.writeValueAsString(value));
            return preparedStatement.executeUpdate() > 0;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Optional<T> internalGetSessionValue(Connection connection, SessionId sessionId, SessionKey<T> key)
    {
        try {
            String query = """
                    SELECT value_json
                    FROM session_values
                    WHERE (session_id = ?) AND (session_key_name = ?) AND (session_key_type = ?)
                    """;
            if (!connection.getAutoCommit()) {
                query += " FOR UPDATE";
            }
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            preparedStatement.setString(1, sessionId.id());
            preparedStatement.setString(2, key.name());
            preparedStatement.setString(3, key.type().getName());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String valueJson = resultSet.getString(1);
                T value = objectMapper.readerFor(key.type()).readValue(valueJson);
                return Optional.of(value);
            }
            return Optional.empty();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
