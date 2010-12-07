package com.proofpoint.dbpool;

import org.testng.annotations.Test;

import java.io.File;
import java.sql.SQLException;

public class H2EmbeddedDataSourceTest
{
    @Test
    public void test()
            throws Exception
    {
        String fileName = File.createTempFile("h2db-", ".db").getAbsolutePath();

        H2EmbeddedDataSourceConfig config = new H2EmbeddedDataSourceConfig()
                .setFilename(fileName)
                .setInitScript("src/test/db/h2.ddl");

        try {
            H2EmbeddedDataSource dataSource = new H2EmbeddedDataSource(config);
            dataSource.getConnection().createStatement().executeQuery("select * from message");
        }
        catch (SQLException e) {
            new File(config.getFilename()).delete();
            throw e;
        }
    }
}
