package com.proofpoint.dbpool;

import org.testng.annotations.Test;

import java.io.File;

public class H2EmbeddedDataSourceTest
{
    @Test
    public void test()
            throws Exception
    {
        String fileName = File.createTempFile("h2db-", ".db").getAbsolutePath();

        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                .setFilename(fileName)
                .setInitScript("com/proofpoint/dbpool/h2.ddl");

        try {
            H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
            dataSource.getConnection().createStatement().executeQuery("select * from message");
        }
        finally {
            new File(config.getFilename()).delete();
        }
    }
}
