package com.proofpoint.dbpool;

import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.proofpoint.units.Duration.nanosSince;
import static java.lang.Math.ceil;

public class ManagedDataSource implements DataSource
{
    private final ConnectionPoolDataSource dataSource;
    private final ManagedSemaphore semaphore;
    private final AtomicInteger maxConnectionWaitMillis = new AtomicInteger(100);
    private final ManagedDataSourceStats stats = new ManagedDataSourceStats();

    public ManagedDataSource(ConnectionPoolDataSource dataSource, int maxConnections, Duration maxConnectionWait)
    {
        if (dataSource == null) {
            throw new NullPointerException("dataSource is null");
        }
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1: maxConnections=" + maxConnections);
        }
        if (maxConnectionWait == null) {
            throw new NullPointerException("maxConnectionWait is null");
        }
        this.dataSource = dataSource;
        semaphore = new ManagedSemaphore(maxConnections);
        maxConnectionWaitMillis.set((int) ceil(maxConnectionWait.toMillis()));
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
            PooledConnection pooledConnection = dataSource.getPooledConnection();
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

        int millis = (int) ceil(maxConnectionWait.toMillis());
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
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out)
            throws SQLException
    {
        dataSource.setLogWriter(out);
    }

    @Override
    public int getLoginTimeout()
            throws SQLException
    {
        return dataSource.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds)
            throws SQLException
    {
        dataSource.setLoginTimeout(seconds);
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
