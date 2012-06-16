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

import io.airlift.configuration.Config;
import io.airlift.configuration.DefunctConfig;

/**
 * see <a href="http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html">http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html</a>
 */
@DefunctConfig({"db.host", "db.port", "db.database", "db.ssl.enabled"})
public class MySqlDataSourceConfig extends ManagedDataSourceConfig<MySqlDataSourceConfig>
{
    private int defaultFetchSize = 100;

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
}
