package com.proofpoint.jmx;

import com.proofpoint.configuration.Config;

public class JMXConfig
{
    @Config("jmx.rmiregistry.port")
    public Integer getRmiRegistryPort()
    {
        return null;
    }

    @Config("jmx.rmiserver.port")
    public Integer getRmiServerPort()
    {
        return null;
    }

    @Config("jmx.rmiserver.hostname")
    public String getRmiServerHostname()
    {
        return null;
    }
}
