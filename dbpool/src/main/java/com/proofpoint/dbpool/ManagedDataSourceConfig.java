package com.proofpoint.dbpool;

import com.proofpoint.configuration.Config;

import java.util.concurrent.TimeUnit;

public class ManagedDataSourceConfig
{
    private String username;
    private String password;
    private int maxConnections = 10;
    private Duration maxConnectionWait = new Duration(30, TimeUnit.MILLISECONDS);

    public String getUsername()
    {
        return username;
    }

    @Config("db.username")
    public ManagedDataSourceConfig setUsername(String username)
    {
        this.username = username;
        return this;
    }

    public String getPassword()
    {
        return password;
    }

    @Config("db.password")
    public ManagedDataSourceConfig setPassword(String password)
    {
        this.password = password;
        return this;
    }

    /**
     * Gets the maximum number of concurrent connections allowed by the data
     * source.  The data source will perform a best effort cap on the number of
     * connections, but in some scenarios there may be slightly more connections
     * than the current cap.  When the cap is lowered the extra connections will
     * be pruned as they are closed.
     */
    public int getMaxConnections()
    {
        return maxConnections;
    }

    /**
     * Sets the maximum number of concurrent connections allowed by the data
     * source.  The data source will perform a best effort cap on the number of
     * connections, but in some scenarios there may be slightly more connections
     * than the current cap.  When the cap is lowered the extra connections will
     * be pruned as they are closed.
     */
    @Config("db.connections.max")
    public ManagedDataSourceConfig setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
        return this;
    }

    /**
     * Gets the maximum time a client is allowed to wait before a connection. If
     * a connection can not be obtained within the limit, a {@link
     * SqlTimeoutException} is thrown.
     */
    public Duration getMaxConnectionWait()
    {
        return maxConnectionWait;
    }

    /**
     * Sets the maximum time a client is allowed to wait before a connection. If
     * a connection can not be obtained within the limit, a {@link
     * SqlTimeoutException} is thrown.
     */
    @Config("db.connections.wait")
    public ManagedDataSourceConfig setMaxConnectionWait(Duration maxConnectionWait)
    {
        this.maxConnectionWait = maxConnectionWait;
        return this;
    }
}
