package com.proofpoint.dbpool;

import com.google.common.io.ByteStreams;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import com.proofpoint.dbpool.H2EmbeddedDataSourceConfig.Cipher;

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
        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                .setFilename(file.getAbsolutePath())
                .setInitScript("com/proofpoint/dbpool/h2.ddl")
                .setCipher(Cipher.AES)
                .setFilePassword("filePassword");

        H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
        dataSource.getConnection().createStatement().executeQuery("select * from message");
    }

    @Test
    public void testInitFromFile()
        throws Exception
    {
        File initScript = File.createTempFile("initscript",".ddl");
        try {

            InputStream in = getClass().getClassLoader().getResourceAsStream("com/proofpoint/dbpool/h2.ddl");
            OutputStream out = new FileOutputStream(initScript);

            ByteStreams.copy(in, out);

            in.close();
            out.close();

            H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                    .setFilename(file.getAbsolutePath())
                    .setInitScript(initScript.getAbsolutePath())
                    .setCipher(Cipher.AES)
                    .setFilePassword("filePassword");

            H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
            dataSource.getConnection().createStatement().executeQuery("select * from message");
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
}
