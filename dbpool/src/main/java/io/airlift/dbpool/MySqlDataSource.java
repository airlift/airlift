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

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceSelector;

import javax.sql.PooledConnection;

import java.sql.SQLException;
import java.util.UUID;

public class MySqlDataSource extends ManagedDataSource
{
    private final ServiceSelector serviceSelector;
    private final int defaultFetchSize;
    private UUID currentServer;
    private MysqlConnectionPoolDataSource dataSource;

    public MySqlDataSource(ServiceSelector serviceSelector, MySqlDataSourceConfig config)
    {
        super(config.getMaxConnections(), config.getMaxConnectionWait());

        this.serviceSelector = serviceSelector;
        this.defaultFetchSize = config.getDefaultFetchSize();
    }

    protected PooledConnection createConnectionInternal()
            throws SQLException
    {
        // attempt to get a connection from the current datasource if we have one
        SQLException lastException = null;
        if (dataSource != null) {
            try {
                return dataSource.getPooledConnection();
            }
            catch (SQLException e) {
                lastException = e;
            }
        }

        // drop reference to current datasource
        dataSource = null;

        // attempt to create a connection to each mysql server (except for the one that we know is bad)
        for (ServiceDescriptor serviceDescriptor : serviceSelector.selectAllServices()) {
            // skip the current server since it is having problems
            if (serviceDescriptor.getId().equals(currentServer)) {
                continue;
            }

            // skip bogus announcements
            String jdbcUrl = serviceDescriptor.getProperties().get("jdbc");
            if (jdbcUrl == null) {
                continue;
            }

            try {
                MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
                dataSource.setUrl(jdbcUrl);
                // these are in MS *not* seconds like everything else in JDBC
                dataSource.setConnectTimeout(getMaxConnectionWaitMillis());
                dataSource.setInitialTimeout(getMaxConnectionWaitMillis());
                dataSource.setDefaultFetchSize(defaultFetchSize);

                PooledConnection connection = dataSource.getPooledConnection();

                // that worked so save the datasource and server id
                this.dataSource = dataSource;
                this.currentServer = serviceDescriptor.getId();
                return connection;
            }
            catch (SQLException e) {
                lastException = e;
            }
        }

        // no servers found, clear the current server id since we no longer have a server at all
        currentServer = null;

        // throw the last exception we got
        if (lastException != null) {
            throw lastException;
        }
        throw new SQLException(String.format("No mysql servers of type '%s' available in pool '%s'", serviceSelector.getType(), serviceSelector.getPool()));
    }
}
