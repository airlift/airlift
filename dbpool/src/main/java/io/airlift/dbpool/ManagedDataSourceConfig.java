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
import io.airlift.units.Duration;

import java.util.concurrent.TimeUnit;

/**
 * Basic configuration for {@link ManagedDataSource}.
 * <p>
 * The configuration options can be chained as follows:
 * <pre>
 * {@code
 *     ManagedDataSourceConfig config = new ManagedDataSourceConfig()
 *             .setMaxConnections(20)
 *             .setMaxConnectionWait(new Duration(20, TimeUnit.MILLISECONDS));
 * }
 *</pre>
 * @param <T> The type current class or subclass, which is used for method chaining
 * <p>
 * Implementation Note:<br>
 * The generic type T is used for method chaining.  A sub class should declare
 * the Class as follows:<br>
 * {@code MyDataSourceConfig extends ManagedDataSourceConfig<MyDataSourceConfig>}
 */
@DefunctConfig({"db.username", "db.password"})
public class ManagedDataSourceConfig<T extends ManagedDataSourceConfig<T>>
{
    private int maxConnections = 10;
    private Duration maxConnectionWait = new Duration(500, TimeUnit.MILLISECONDS);

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
