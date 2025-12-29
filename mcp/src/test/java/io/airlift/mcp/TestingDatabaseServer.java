package io.airlift.mcp;

import com.google.common.io.Closer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import jakarta.annotation.PreDestroy;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@SuppressWarnings("FieldCanBeLocal")
public class TestingDatabaseServer
        implements Closeable
{
    private final Closer closer;
    private final PostgreSQLContainer postgres;
    private final HikariPool hikariPool;

    public TestingDatabaseServer()
    {
        closer = Closer.create();
        postgres = new PostgreSQLContainer("postgres:9.6.12");
        closer.register(postgres::close);
        postgres.start();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(postgres.getJdbcUrl());
        hikariConfig.setUsername(postgres.getUsername());
        hikariConfig.setPassword(postgres.getPassword());
        hikariPool = new HikariPool(hikariConfig);
        closer.register(() -> {
            try {
                hikariPool.shutdown();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @PreDestroy
    @Override
    public void close()
            throws IOException
    {
        closer.close();
    }

    public interface WithConnection<T>
    {
        T apply(Connection connection)
                throws Exception;
    }

    public interface InConnection
    {
        void accept(Connection connection)
                throws Exception;
    }

    public <T> T withTransaction(WithConnection<T> handler)
    {
        try (Connection connection = hikariPool.getConnection()) {
            connection.setAutoCommit(false);
            T result;
            try {
                result = handler.apply(connection);
            }
            catch (Exception e) {
                try {
                    connection.rollback();
                }
                catch (SQLException ex) {
                    e.addSuppressed(ex);
                }
                throw new RuntimeException(e);
            }
            connection.commit();
            return result;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void inTransaction(InConnection consumer)
    {
        withTransaction(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    public Connection newRawConnection()
            throws SQLException
    {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    public <T> T withConnection(WithConnection<T> handler)
    {
        try (Connection connection = hikariPool.getConnection()) {
            return handler.apply(connection);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void inConnection(InConnection consumer)
    {
        withConnection(connection -> {
            consumer.accept(connection);
            return null;
        });
    }
}
