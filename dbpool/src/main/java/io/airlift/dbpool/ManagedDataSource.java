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

import com.google.common.primitives.Ints;
import io.airlift.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static io.airlift.units.Duration.nanosSince;
import static java.lang.Math.ceil;

public abstract class ManagedDataSource implements DataSource
{
    private final ManagedSemaphore semaphore;
    private final AtomicInteger maxConnectionWaitMillis = new AtomicInteger(100);
    private final ManagedDataSourceStats stats = new ManagedDataSourceStats();

    protected ManagedDataSource(int maxConnections, Duration maxConnectionWait)
    {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1: maxConnections=" + maxConnections);
        }
        if (maxConnectionWait == null) {
            throw new NullPointerException("maxConnectionWait is null");
        }
        semaphore = new ManagedSemaphore(maxConnections);
        maxConnectionWaitMillis.set(Ints.checkedCast(maxConnectionWait.toMillis()));
    }

    @Override
    public Connection getConnection()
            throws SQLException
    {
        long start = System.nanoTime();
        try {
            acquirePermit();

            boolean checkedOut = false;
            try {
                Connection connection = createConnection();
                checkedOut = true;
                return connection;
            }
            finally {
                if (!checkedOut) {
                    semaphore.release();
                }
            }
        }
        finally {
            stats.connectionCheckedOut(nanosSince(start));
        }
    }

    protected Connection createConnection()
            throws SQLException
    {
        boolean success = false;
        try {
            // todo do not create on caller's thread
            long start = System.nanoTime();
            PooledConnection pooledConnection = createConnectionInternal();
            Connection connection = prepareConnection(pooledConnection);
            stats.connectionCreated(nanosSince(start));

            success = true;

            return connection;
        }
        finally {
            if (!success) {
                stats.creationErrorOccurred();
            }
        }
    }

    protected abstract PooledConnection createConnectionInternal()
            throws SQLException;

    protected Connection prepareConnection(PooledConnection pooledConnection)
            throws SQLException
    {
        Connection connection = pooledConnection.getConnection();
        pooledConnection.addConnectionEventListener(new NoPoolConnectionEventListener());
        return connection;
    }

    protected void connectionReturned(PooledConnection pooledConnection, long checkoutTime)
    {
        try {
            // todo do not close on caller's thread
            pooledConnection.close();
        }
        catch (SQLException ignored) {
            // hey we tried
        }
    }

    protected void connectionDestroyed(PooledConnection pooledConnection, long checkoutTime)
    {
    }

    @Managed
    public int getMaxConnectionWaitMillis()
    {
        return maxConnectionWaitMillis.get();
    }

    @Managed
    public void setMaxConnectionWaitMillis(Duration maxConnectionWait)
            throws IllegalArgumentException
    {
        if (maxConnectionWait == null) {
            throw new NullPointerException("maxConnectionWait is null");
        }

        int millis = Ints.checkedCast(maxConnectionWait.toMillis());
        if (millis < 1) {
            throw new IllegalArgumentException("maxConnectionWait must be greater than 1 millisecond");
        }
        this.maxConnectionWaitMillis.set(millis);
    }

    @Managed
    public long getConnectionsActive()
    {
        return semaphore.getActivePermits();
    }

    @Managed
    public int getMaxConnections()
    {
        return semaphore.getPermits();
    }

    @Managed
    public void setMaxConnections(int maxConnections)
    {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1: maxConnections=" + maxConnections);
        }
        semaphore.setPermits(maxConnections);
    }

    @Managed
    @Flatten
    public ManagedDataSourceStats getStats()
    {
        return stats;
    }

    @Override
    public PrintWriter getLogWriter()
            throws SQLException
    {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out)
            throws SQLException
    {
    }

    @Override
    public Logger getParentLogger()
            throws SQLFeatureNotSupportedException
    {
        throw new SQLFeatureNotSupportedException("java.util.logging not supported");
    }

    @Override
    public int getLoginTimeout()
            throws SQLException
    {
        return (int) ceil(getMaxConnectionWaitMillis() / 1000.0);
    }

    @Override
    public void setLoginTimeout(int seconds)
            throws SQLException
    {
    }


    @Override
    public boolean isWrapperFor(Class<?> iface)
            throws SQLException
    {
        if (iface == null) {
            throw new SQLException("iface is null");
        }

        return iface.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> iface)
            throws SQLException
    {
        if (iface == null) {
            throw new SQLException("iface is null");
        }

        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException(getClass().getName() + " does not implement " + iface.getName());
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public final Connection getConnection(String username, String password)
            throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    private void acquirePermit()
            throws SQLException
    {
        int timeout = maxConnectionWaitMillis.get();
        try {
            if (!semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
                throw new SqlTimeoutException("Could not acquire a connection within " + timeout + " msec");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqlTimeoutException("Interrupted while waiting for connection", e);
        }
    }

    protected class NoPoolConnectionEventListener implements ConnectionEventListener
    {
        private final long checkoutTime = System.nanoTime();
        private final AtomicBoolean returned = new AtomicBoolean();

        @Override
        public void connectionClosed(ConnectionEvent event)
        {
            // was the connection already returned
            if (!returned.compareAndSet(false, true)) {
                return;
            }

            PooledConnection pooledConnection = null;
            try {
                pooledConnection = (PooledConnection) event.getSource();
                pooledConnection.removeConnectionEventListener(this);

                stats.connectionReturned(nanosSince(checkoutTime));
            }
            finally {
                semaphore.release();

                if (pooledConnection != null) {
                    connectionReturned(pooledConnection, checkoutTime);
                }
            }
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event)
        {
            // was the connection already returned
            if (!returned.compareAndSet(false, true)) {
                return;
            }

            PooledConnection pooledConnection = null;
            try {
                pooledConnection = (PooledConnection) event.getSource();
                pooledConnection.removeConnectionEventListener(this);

                stats.connectionErrorOccurred();
            }
            finally {
                semaphore.release();

                if (pooledConnection != null) {
                    connectionDestroyed(pooledConnection, checkoutTime);
                }
            }
        }
    }

}
