package com.proofpoint.dbpool;

import com.proofpoint.configuration.Config;

import java.util.concurrent.TimeUnit;

/**
 * see <a href="http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html">http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html</a>
 */
public class MySqlDataSourceConfig
{
    private String host;
    private int port = 3306;
    private String databaseName;
    private String username;
    private String password;
    private int maxConnections = 10;
    private Duration maxConnectionWait = new Duration(30, TimeUnit.MILLISECONDS);
    private int defaultFetchSize = 100;
    private boolean useSsl;

    public String getHost()
    {
        return host;
    }

    @Config("db.host")
    public void setHost(String host)
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    @Config("db.port")
    public void setPort(int port)
    {
        this.port = port;
    }

    public String getDatabaseName()
    {
        return databaseName;
    }

    @Config("db.database")
    public void setDatabaseName(String databaseName)
    {
        this.databaseName = databaseName;
    }

    public String getUsername()
    {
        return username;
    }

    @Config("db.username")
    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    @Config("db.password")
    public void setPassword(String password)
    {
        this.password = password;
    }

    /**
     * Gets the maximum number of concurrent connections allowed by the data
     * source.  The data source will perform a best effort cap on the number
     * of connections, but in some scenarios there may be slightly more
     * connections than the current cap.  When the cap is lowered the extra
     * connections will be pruned as they are closed.
     */
    public int getMaxConnections()
    {
        return maxConnections;
    }

    /**
     * Sets the maximum number of concurrent connections allowed by the data
     * source.  The data source will perform a best effort cap on the number
     * of connections, but in some scenarios there may be slightly more
     * connections than the current cap.  When the cap is lowered the extra
     * connections will be pruned as they are closed.
     */
    @Config("db.connections.max")
    public void setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
    }

    /**
     * Gets the maximum time a client is allowed to wait before a connection.
     * If a connection can not be obtained within the limit, a
     * {@link SqlTimeoutException} is thrown.
     */
    public Duration getMaxConnectionWait()
    {
        return maxConnectionWait;
    }

    /**
     * Sets the maximum time a client is allowed to wait before a connection.
     * If a connection can not be obtained within the limit, a
     * {@link SqlTimeoutException} is thrown.
     */
    @Config("db.connections.wait")
    public void setMaxConnectionWait(Duration maxConnectionWait)
    {
        this.maxConnectionWait = maxConnectionWait;
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
    public void setDefaultFetchSize(int defaultFetchSize)
    {
        this.defaultFetchSize = defaultFetchSize;
    }

    public boolean getUseSsl()
    {
        return useSsl;
    }

    @Config("db.ssl.enabled")
    public void setUseSsl(boolean useSsl)
    {
        this.useSsl = useSsl;
    }
}
