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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.airlift.dbpool.H2EmbeddedDataSourceConfig.Cipher;
import org.h2.jdbc.JdbcSQLException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Test(singleThreaded = true)
public class H2EmbeddedDataSourceTest
{
    private File file;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile("h2db-", ".db");
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        file.delete();
    }

    @Test
    public void testInitFromResource()
            throws Exception
    {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                    .setFilename(file.getAbsolutePath())
                    .setInitScript("io/airlift/dbpool/h2.ddl")
                    .setCipher(Cipher.AES)
                    .setFilePassword("filePassword");

            H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            resultSet = statement.executeQuery("select * from message");
        }
        finally {
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    @Test
    public void testInitFromFile()
            throws Exception
    {
        File initScript = File.createTempFile("initscript", ".ddl");
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            URL url = Resources.getResource("io/airlift/dbpool/h2.ddl");
            Resources.asByteSource(url).copyTo(Files.asByteSink(initScript));

            H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                    .setFilename(file.getAbsolutePath())
                    .setInitScript(initScript.getAbsolutePath())
                    .setCipher(Cipher.AES)
                    .setFilePassword("filePassword");

            H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            resultSet = statement.executeQuery("select * from message");
        }
        finally {
            initScript.delete();
            closeQuietly(resultSet);
            closeQuietly(statement);
            closeQuietly(connection);
        }
    }

    @Test(expectedExceptions = JdbcSQLException.class)
    public void testInitFromInvalidDdlThrows()
            throws Exception
    {
        File initScript = File.createTempFile("initscript", ".ddl");
        try {
            String invalidDdl = "This isn't valid SQL";
            Files.write(invalidDdl, initScript, Charsets.UTF_8);

            H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                    .setFilename(file.getAbsolutePath())
                    .setInitScript(initScript.getAbsolutePath())
                    .setCipher(Cipher.AES)
                    .setFilePassword("filePassword");

            new H2EmbeddedDataSource(config);
        }
        finally {
            initScript.delete();
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullFilenameThrows()
            throws Exception
    {
        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                // Filename left as null
                .setInitScript("io/airlift/dbpool/h2.ddl")
                .setCipher(Cipher.AES)
                .setFilePassword("filePassword");

        new H2EmbeddedDataSource(config);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyFilenameThrows()
            throws Exception
    {
        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                .setFilename("")
                .setInitScript("io/airlift/dbpool/h2.ddl")
                .setCipher(Cipher.AES)
                .setFilePassword("filePassword");

        new H2EmbeddedDataSource(config);
    }

    @Test(expectedExceptions = FileNotFoundException.class)
    public void testCantFindInitScript()
            throws Exception
    {
        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                .setFilename(file.getAbsolutePath())
                .setInitScript("foo")
                .setCipher(Cipher.AES)
                .setFilePassword("filePassword");

        new H2EmbeddedDataSource(config);
    }

    private static void closeQuietly(ResultSet resultSet)
    {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(Statement statement)
    {
        try {
            if (statement != null) {
                statement.close();
            }
        }
        catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(Connection connection)
    {
        try {
            if (connection != null) {
                connection.close();
            }
        }
        catch (Throwable ignored) {
        }
    }
}
