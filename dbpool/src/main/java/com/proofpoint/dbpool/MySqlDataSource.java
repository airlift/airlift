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
