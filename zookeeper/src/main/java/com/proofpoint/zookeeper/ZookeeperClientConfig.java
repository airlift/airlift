package com.proofpoint.zookeeper;

import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.Default;

public interface ZookeeperClientConfig
{
    @Config("zookeeper.connection-string")
    @ConfigDescription("Zookeeper connection string")
    String getConnectionString();

    @Config("zookeeper.session-timeout-in-ms")
    @Default("60000")            
    @ConfigDescription("Zookeeper session timeout in ms")
    int getSessionTimeoutInMs();

    @Config("zookeeper.connection-timeout-in-ms")
    @Default("10000")
    @ConfigDescription("Zookeeper connection timeout in ms")
    int getConnectionTimeoutInMs();

    @Config("zookeeper.connection-max-retries")
    @Default("2")
    @ConfigDescription("Max number of times to retry connecting to the ZK cluster before failing")
    int getMaxConnectionLossRetries();

    @Config("zookeeper.connection-loss-sleep")
    @Default("1000")
    @ConfigDescription("Amount of time in ms to sleep between connection loss retries")
    int getConnectionLossSleepInMs();
}
