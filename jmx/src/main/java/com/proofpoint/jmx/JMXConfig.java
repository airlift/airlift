package com.proofpoint.jmx;

import com.proofpoint.configuration.Config;

public final class JMXConfig
{
    private Integer rmiRegistryPort;
    private Integer rmiServerPort;
    private String hostname;

    @Config("jmx.rmiregistry.port")
    public Integer getRmiRegistryPort()
    {
        return rmiRegistryPort;
    }

    public JMXConfig setRmiRegistryPort(Integer rmiRegistryPort)
    {
        this.rmiRegistryPort = rmiRegistryPort;
        return this;
    }

    @Config("jmx.rmiserver.port")
    public Integer getRmiServerPort()
    {
        return rmiServerPort;
    }

    public JMXConfig setRmiServerPort(Integer rmiServerPort)
    {
        this.rmiServerPort = rmiServerPort;
        return this;
    }

    @Config("jmx.rmiserver.hostname")
    public String getHostname()
    {
        return hostname;
    }

    public JMXConfig setHostname(String hostname)
    {
        this.hostname = hostname;
        return this;
    }
}
