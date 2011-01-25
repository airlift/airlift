package com.proofpoint.jmx;

import com.proofpoint.configuration.Config;

public final class JMXConfig
{
    private Integer rmiRegistryPort;
    private Integer rmiServerPort;
    private String hostname;

    public Integer getRmiRegistryPort()
    {
        return rmiRegistryPort;
    }

    @Config("jmx.rmiregistry.port")
    public JMXConfig setRmiRegistryPort(Integer rmiRegistryPort)
    {
        this.rmiRegistryPort = rmiRegistryPort;
        return this;
    }

    public Integer getRmiServerPort()
    {
        return rmiServerPort;
    }

    @Config("jmx.rmiserver.port")
    public JMXConfig setRmiServerPort(Integer rmiServerPort)
    {
        this.rmiServerPort = rmiServerPort;
        return this;
    }

    public String getHostname()
    {
        return hostname;
    }

    @Config("jmx.rmiserver.hostname")
    public JMXConfig setHostname(String hostname)
    {
        this.hostname = hostname;
        return this;
    }
}
