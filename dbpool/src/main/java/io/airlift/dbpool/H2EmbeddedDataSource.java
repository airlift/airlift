/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.dbpool;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import io.airlift.dbpool.H2EmbeddedDataSourceConfig.Cipher;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.util.ScriptReader;

import javax.sql.PooledConnection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

public class H2EmbeddedDataSource extends ManagedDataSource
{
    private final JdbcDataSource dataSource;

    @Inject
    public H2EmbeddedDataSource(H2EmbeddedDataSourceConfig config)
            throws Exception
    {
        super(config.getMaxConnections(), config.getMaxConnectionWait());

        Preconditions.checkNotNull(config.getFilename());
        if (config.getFilename().isEmpty()) {
            throw new IllegalArgumentException("filename is empty");
        }

        // build jdbc url connection string
        StringBuilder jdbcUrlBuilder = new StringBuilder()
                .append("jdbc:h2:").append(config.getFilename())
                .append(";MVCC=").append(config.isMvccEnabled());

        if (config.getCipher() != Cipher.NONE) {
            jdbcUrlBuilder.append(";CIPHER=").append(config.getCipher());
        }

        String jdbcUrl = jdbcUrlBuilder.toString();

        // create dataSource
        dataSource = new JdbcDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser("sa");
        if (config.getCipher() != Cipher.NONE) {
            dataSource.setPassword(config.getFilePassword() + " ");
        }
        else {
            dataSource.setPassword("");
        }
        dataSource.setLoginTimeout(Ints.checkedCast(config.getMaxConnectionWait().roundTo(SECONDS)));

        // connect to database and initialize database
        Connection connection = getConnection();
        try {
            setConfig(connection, "CACHE_SIZE", config.getCacheSize());
            setConfig(connection, "COMPRESS_LOB", config.getCompressLob());
            setConfig(connection, "MAX_MEMORY_ROWS", config.getMaxMemoryRows());
            setConfig(connection, "MAX_LENGTH_INPLACE_LOB", config.getMaxLengthInplaceLob());
            setConfig(connection, "DB_CLOSE_DELAY ", "-1");

            // handle init script
            String fileName = config.getInitScript();
            if (fileName != null) {
                // find init script
                File file = new File(fileName);
                URL url;
                if (file.exists()) {
                    url = file.toURI().toURL();
                }
                else {
                    url = getClass().getClassLoader().getResource(fileName);
                }

                if (url == null) {
                    throw new FileNotFoundException(fileName);
                }

                // execute init script
                try (Reader reader = Resources.asCharSource(url, UTF_8).openStream()) {
                    ScriptReader scriptReader = new ScriptReader(reader);
                    for (String statement = scriptReader.readStatement(); statement != null; statement = scriptReader.readStatement()) {
                        executeCommand(connection, statement);
                    }
                }
            }

            // run last so script can contain literals
            setConfig(connection, "ALLOW_LITERALS", config.getAllowLiterals());
        }
        finally {
            closeQuietly(connection);
        }
    }

    @Override
    protected PooledConnection createConnectionInternal()
            throws SQLException
    {
        return dataSource.getPooledConnection();
    }

    private static void setConfig(Connection connection, String name, Object value)
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


    private static void executeCommand(Connection connection, String command)
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
