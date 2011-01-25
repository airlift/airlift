package com.proofpoint.dbpool;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

import static java.lang.Math.ceil;
import static java.util.concurrent.TimeUnit.SECONDS;

public class MySqlDataSource extends ManagedDataSource
{
    public MySqlDataSource(MySqlDataSourceConfig config)
    {
        super(createMySQLConnectionPoolDataSource(config),
                config.getMaxConnections(),
                config.getMaxConnectionWait());
    }

    private static MysqlConnectionPoolDataSource createMySQLConnectionPoolDataSource(MySqlDataSourceConfig config)
    {
        MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
        dataSource.setServerName(config.getHost());
        dataSource.setUser(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setPort(config.getPort());
        dataSource.setDatabaseName(config.getDatabaseName());
        dataSource.setConnectTimeout((int) ceil(config.getMaxConnectionWait().convertTo(SECONDS)));
        dataSource.setInitialTimeout((int) ceil(config.getMaxConnectionWait().convertTo(SECONDS)));
        dataSource.setDefaultFetchSize(config.getDefaultFetchSize());
        dataSource.setUseSSL(config.getUseSsl());
        return dataSource;
    }
}
