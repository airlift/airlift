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
    private String  sessionStorePath;

    public String getSessionStorePath()
    {
        return sessionStorePath;
    }

    @Config("zookeeper.session-id-local-path")
    @ConfigDescription("File to store the Zookeeper session ID in. This is optional - specify only if session re-use is needed.")
    public void setSessionStorePath(String sessionStorePath)
    {
        this.sessionStorePath = sessionStorePath;
    }

    public String getConnectionString()
    {
        return connectionString;
    }

    public int getSessionTimeoutInMs()
    {
        return sessionTimeoutInMs;
    }

    public int getConnectionTimeoutInMs()
    {
        return connectionTimeoutInMs;
    }

    public int getMaxConnectionLossRetries()
    {
        return maxConnectionLossRetries;
    }

    public int getConnectionLossSleepInMs()
    {
        return connectionLossSleepInMs;
    }

    @Config("zookeeper.connection-string")
    @ConfigDescription("Zookeeper connection string")
    public void setConnectionString(String connectionString)
    {
        this.connectionString = connectionString;
    }

    @Config("zookeeper.session-timeout-in-ms")
    @ConfigDescription("Zookeeper session timeout in ms")
    public void setSessionTimeoutInMs(int sessionTimeoutInMs)
    {
        this.sessionTimeoutInMs = sessionTimeoutInMs;
    }

    @Config("zookeeper.connection-timeout-in-ms")
    @ConfigDescription("Zookeeper connection timeout in ms")
    public void setConnectionTimeoutInMs(int connectionTimeoutInMs)
    {
        this.connectionTimeoutInMs = connectionTimeoutInMs;
    }

    @Config("zookeeper.connection-max-retries")
    @ConfigDescription("Max number of times to retry connecting to the ZK cluster before failing")
    public void setMaxConnectionLossRetries(int maxConnectionLossRetries)
    {
        this.maxConnectionLossRetries = maxConnectionLossRetries;
    }

    @Config("zookeeper.connection-loss-sleep")
    @ConfigDescription("Amount of time in ms to sleep between connection loss retries")
    public void setConnectionLossSleepInMs(int connectionLossSleepInMs)
    {
        this.connectionLossSleepInMs = connectionLossSleepInMs;
    }
}
