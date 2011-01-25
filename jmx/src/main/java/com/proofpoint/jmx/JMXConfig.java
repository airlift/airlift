package com.proofpoint.jmx;

import com.proofpoint.configuration.Config;

public final class JmxConfig
{
    private Integer rmiRegistryPort;
    private Integer rmiServerPort;
    private String hostname;

    public Integer getRmiRegistryPort()
    {
        return rmiRegistryPort;
    }

    @Config("jmx.rmiregistry.port")
    public JmxConfig setRmiRegistryPort(Integer rmiRegistryPort)
    {
        this.rmiRegistryPort = rmiRegistryPort;
        return this;
    }

    public Integer getRmiServerPort()
    {
        return rmiServerPort;
    }

    @Config("jmx.rmiserver.port")
    public JmxConfig setRmiServerPort(Integer rmiServerPort)
    {
        this.rmiServerPort = rmiServerPort;
        return this;
    }

    public String getHostname()
    {
        return hostname;
    }

    @Config("jmx.rmiserver.hostname")
    public JmxConfig setHostname(String hostname)
    {
        this.hostname = hostname;
        return this;
    }
}
