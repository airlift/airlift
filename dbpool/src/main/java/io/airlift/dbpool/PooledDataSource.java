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

import io.airlift.units.Duration;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is not complete yet.
 */
abstract class PooledDataSource
        extends ManagedDataSource
{
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final Deque<PooledConnection> pool = new LinkedBlockingDeque<>();

    PooledDataSource(ConnectionPoolDataSource dataSource, int maxConnections, Duration maxConnectionWait)
    {
        super(maxConnections, maxConnectionWait);
    }

    public void dispose()
    {
        disposed.set(true);

        // there is really no harm in running this loop every time dispose is called
        for (PooledConnection pooledConnection = pool.poll(); pooledConnection != null; pooledConnection = pool.poll()) {
            try {
                pooledConnection.close();
            }
            catch (SQLException ignored) {
            }
        }
    }

    @Override
    protected Connection createConnection()
            throws SQLException
    {
        assertNotDisposed();

        // check for a pooled connection
        PooledConnection pooledConnection = pool.pollLast();
        if (pooledConnection != null) {
            return prepareConnection(pooledConnection);
        }

        // otherwise create a new one
        return super.createConnection();
    }

    @Override
    protected void connectionReturned(PooledConnection pooledConnection, long checkoutTime)
    {
        // if this pool has been disposed, or if we have too many connections already
        if (disposed.get() || getConnectionsActive() + pool.size() > getMaxConnections()) {
            // close this connection
            super.connectionReturned(pooledConnection, checkoutTime);
        }
        else {
            // otherwise add it to the pool
            pool.addLast(pooledConnection);
        }
    }

    private void assertNotDisposed()
            throws SQLException
    {
        if (disposed.get()) {
            throw new SQLException(getClass().getSimpleName() + " has been disposed");
        }
    }
}
