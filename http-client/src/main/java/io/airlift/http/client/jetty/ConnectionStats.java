/*
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
package io.airlift.http.client.jetty;

import org.eclipse.jetty.io.ConnectionStatistics;
import org.weakref.jmx.Managed;

import static java.util.Objects.requireNonNull;

public class ConnectionStats
{
    private final ConnectionStatistics connectionStats;

    public ConnectionStats(ConnectionStatistics connectionStats)
    {
        this.connectionStats = requireNonNull(connectionStats, "connectionStats is null");
    }

    @Managed(description = "total number of bytes received by all connections")
    public long getReceivedBytes()
    {
        return connectionStats.getReceivedBytes();
    }

    @Managed(description = "total number of bytes sent by all connections")
    public long getSentBytes()
    {
        return connectionStats.getSentBytes();
    }

    @Managed(description = "max connection duration (ms)")
    public long getMaxConnectionDuration()
    {
        return connectionStats.getConnectionDurationMax();
    }

    @Managed(description = "mean connection duration (ms)")
    public double getMeanConnectionDuration()
    {
        return connectionStats.getConnectionDurationMean();
    }

    @Managed(description = "standard deviation of connection duration")
    public double getConnectionDurationStdDev()
    {
        return connectionStats.getConnectionDurationStdDev();
    }

    @Managed(description = "total number of connections opened")
    public long getTotalConnectionCount()
    {
        return connectionStats.getConnectionsTotal();
    }

    @Managed(description = "number of open connections")
    public long getOpenConnectionCount()
    {
        return connectionStats.getConnections();
    }

    @Managed(description = "max number of open connections")
    public long getMaxOpenConnections()
    {
        return connectionStats.getConnectionsMax();
    }

    @Managed(description = "total number of messages received")
    public long getReceivedMessageCount()
    {
        return connectionStats.getReceivedMessages();
    }

    @Managed(description = "total number of messages sent")
    public long getSentMessageCount()
    {
        return connectionStats.getSentMessages();
    }
}
