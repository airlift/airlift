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

import com.proofpoint.configuration.Config;

/**
 * see <a href="http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html">http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html</a>
 */
public class MySqlDataSourceConfig extends ManagedDataSourceConfig<MySqlDataSourceConfig>
{
    private String host;
    private int port = 3306;
    private String databaseName;
    private int defaultFetchSize = 100;
    private boolean useSsl;

    public String getHost()
    {
        return host;
    }

    @Config("db.host")
    public MySqlDataSourceConfig setHost(String host)
    {
        this.host = host;
        return this;
    }

    public int getPort()
    {
        return port;
    }

    @Config("db.port")
    public MySqlDataSourceConfig setPort(int port)
    {
        this.port = port;
        return this;
    }

    public String getDatabaseName()
    {
        return databaseName;
    }

    @Config("db.database")
    public MySqlDataSourceConfig setDatabaseName(String databaseName)
    {
        this.databaseName = databaseName;
        return this;
    }

    /**
     * Gets the default fetch size for all connection.
     */
    public int getDefaultFetchSize()
    {
        return defaultFetchSize;
    }

    /**
     * Sets the default fetch size for all connection.
     */
    @Config("db.fetch-size")
    public MySqlDataSourceConfig setDefaultFetchSize(int defaultFetchSize)
    {
        this.defaultFetchSize = defaultFetchSize;
        return this;
    }

    public boolean getUseSsl()
    {
        return useSsl;
    }

    @Config("db.ssl.enabled")
    public MySqlDataSourceConfig setUseSsl(boolean useSsl)
    {
        this.useSsl = useSsl;
        return this;
    }
}
