package com.proofpoint.dbpool;

import com.google.inject.Inject;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.util.ScriptReader;

import java.io.FileReader;
import java.io.File;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileNotFoundException;
import static java.lang.Math.ceil;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import static java.util.concurrent.TimeUnit.SECONDS;

public class H2EmbeddedDataSource extends ManagedDataSource
{
    @Inject
    public H2EmbeddedDataSource(H2EmbeddedDataSourceConfig config)
            throws Exception
    {
        super(createMySQLConnectionPoolDataSource(config),
                config.getMaxConnections(),
                config.getMaxConnectionWait());


        Connection connection = getConnection();
        try {
            setConfig(connection, "CACHE_SIZE", config.getCacheSize());
            Reader fileReader;
            String fileName = config.getInitScript();
            if (new File(fileName).exists()) {
                fileReader = new FileReader(fileName);
            } else {
                InputStream stream = getClass().getClassLoader().getResourceAsStream(fileName);
                if (stream == null) {
                    throw new FileNotFoundException(fileName);
                }
                fileReader = new InputStreamReader(stream);
            }
            ScriptReader scriptReader = new ScriptReader(fileReader);
            for (String statement = scriptReader.readStatement(); statement != null; statement = scriptReader.readStatement()) {
                executeCommand(connection, statement);
            }

            // run last so script can contain literals
            setConfig(connection, "ALLOW_LITERALS", config.getAllowLiterals());
        }
        finally {
            closeQuietly(connection);
        }
    }

    private static JdbcDataSource createMySQLConnectionPoolDataSource(H2EmbeddedDataSourceConfig config)
            throws Exception
    {

        String url = new StringBuilder()
                .append("jdbc:h2:").append(config.getFilename())
                .append(";ALLOW_LITERALS=").append(config.getAllowLiterals())
                .append(";CACHE_SIZE=").append(config.getCacheSize())
                .toString();

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setLoginTimeout((int) ceil(config.getMaxConnectionWait().convertTo(SECONDS)));

        return dataSource;
    }

    private void setConfig(Connection connection, String name, Object value)
            throws SQLException
    {
        Statement statement = connection.createStatement();
        try {
            String command = String.format("SET %s %s", name, value);
            int count = statement.executeUpdate(command);
            if (count != 0) {
                throw new SQLException("Failed to execute command: " + command);
            }
        }
        finally {
            closeQuietly(statement);
        }
    }


    private void executeCommand(Connection connection, String command)
            throws SQLException
    {
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate(command);
        }
        finally {
            closeQuietly(statement);
        }
    }

    private static void closeQuietly(Statement statement)
    {
        try {
            statement.close();
        }
        catch (SQLException ignored) {
        }
    }

    private static void closeQuietly(Connection connection)
    {
        try {
            connection.close();
        }
        catch (SQLException ignored) {
        }
    }
}