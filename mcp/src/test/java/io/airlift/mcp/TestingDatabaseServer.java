package io.airlift.mcp;

import com.google.common.io.Closer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;

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

    public <T> T withConnection(WithConnection<T> handler)
    {
        try (Connection connection = hikariPool.getConnection()) {
            return handler.apply(connection);
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
