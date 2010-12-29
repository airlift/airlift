package com.proofpoint.zookeeper;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

public class ZookeeperClientConfig
{
    private String  connectionString;
    private int     sessionTimeoutInMs = 60000;
    private int     connectionTimeoutInMs = 10000;
    private int     maxConnectionLossRetries = 2;
    private int     connectionLossSleepInMs = 1000;

    @Config("zookeeper.connection-string")
    @ConfigDescription("Zookeeper connection string")
    public String getConnectionString()
    {
        return connectionString;
    }

    @Config("zookeeper.session-timeout-in-ms")
    @ConfigDescription("Zookeeper session timeout in ms")
    public int getSessionTimeoutInMs()
    {
        return sessionTimeoutInMs;
    }

    @Config("zookeeper.connection-timeout-in-ms")
    @ConfigDescription("Zookeeper connection timeout in ms")
    public int getConnectionTimeoutInMs()
    {
        return connectionTimeoutInMs;
    }

    @Config("zookeeper.connection-max-retries")
    @ConfigDescription("Max number of times to retry connecting to the ZK cluster before failing")
    public int getMaxConnectionLossRetries()
    {
        return maxConnectionLossRetries;
    }

    @Config("zookeeper.connection-loss-sleep")
    @ConfigDescription("Amount of time in ms to sleep between connection loss retries")
    public int getConnectionLossSleepInMs()
    {
        return connectionLossSleepInMs;
    }

    public void setConnectionString(String connectionString)
    {
        this.connectionString = connectionString;
    }

    public void setSessionTimeoutInMs(int sessionTimeoutInMs)
    {
        this.sessionTimeoutInMs = sessionTimeoutInMs;
    }

    public void setConnectionTimeoutInMs(int connectionTimeoutInMs)
    {
        this.connectionTimeoutInMs = connectionTimeoutInMs;
    }

    public void setMaxConnectionLossRetries(int maxConnectionLossRetries)
    {
        this.maxConnectionLossRetries = maxConnectionLossRetries;
    }

    public void setConnectionLossSleepInMs(int connectionLossSleepInMs)
    {
        this.connectionLossSleepInMs = connectionLossSleepInMs;
    }
}
