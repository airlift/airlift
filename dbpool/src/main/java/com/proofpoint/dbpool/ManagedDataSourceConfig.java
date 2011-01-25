package com.proofpoint.dbpool;

import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;

import java.util.concurrent.TimeUnit;

/**
 * Basic configuration for {@see ManagedDataSource}.
 * </p>
 * The configuration options can be chained as follows:<br>
 * {@code
 *     ManagedDataSourceConfig config = new ManagedDataSourceConfig()
 *             .setUsername("username")
 *             .setPassword("password")
 *             .setMaxConnections(20)
 *             .setMaxConnectionWait(new Duration(20, TimeUnit.MILLISECONDS));
 * }
 *
 * @param <T> The type current class or subclass, which is used for method chaining
 * <p/>
 * Implementation Note:</br>
 * The generic type T is used for method chaining.  A sub class should declare
 * the Class as follows:</br>
 * {@code MyDataSourceConfig extends ManagedDataSourceConfig<MyDataSourceConfig>}
 */
public class ManagedDataSourceConfig<T extends ManagedDataSourceConfig<T>>
{
    private String username;
    private String password;
    private int maxConnections = 10;
    private Duration maxConnectionWait = new Duration(500, TimeUnit.MILLISECONDS);

    public String getUsername()
    {
        return username;
    }

    @Config("db.username")
    public T setUsername(String username)
    {
        this.username = username;
        return (T) this;
    }

    public String getPassword()
    {
        return password;
    }

    @Config("db.password")
    public T setPassword(String password)
    {
        this.password = password;
        return (T) this;
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
    public T setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
        return (T) this;

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
    public T setMaxConnectionWait(Duration maxConnectionWait)
    {
        this.maxConnectionWait = maxConnectionWait;
        return (T) this;

    }
}
