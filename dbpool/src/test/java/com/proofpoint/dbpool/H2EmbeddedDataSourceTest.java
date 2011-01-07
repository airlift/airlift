package com.proofpoint.dbpool;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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
    public void test()
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
}
