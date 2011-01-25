package com.proofpoint.dbpool;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.proofpoint.dbpool.H2EmbeddedDataSourceConfig.Cipher;
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

public class H2EmbeddedDataSourceTest
{
    private File file;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile("h2db-", ".db");
    }

    @AfterMethod
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
                    .setInitScript("com/proofpoint/dbpool/h2.ddl")
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
            URL url = Resources.getResource("com/proofpoint/dbpool/h2.ddl");
            Files.copy(Resources.newReaderSupplier(url, Charsets.UTF_8), initScript, Charsets.UTF_8);

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
                .setInitScript("com/proofpoint/dbpool/h2.ddl")
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
                .setInitScript("com/proofpoint/dbpool/h2.ddl")
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
