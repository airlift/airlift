package com.proofpoint.discovery.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import java.net.URI;

public class DiscoveryClientConfig
{
    private URI discoveryServiceURI;

    @Deprecated
    public URI getDiscoveryServiceURI()
    {
        return discoveryServiceURI;
    }

    @Config("discovery.uri")
    @ConfigDescription("Discovery service base URI")
    @Deprecated
    public DiscoveryClientConfig setDiscoveryServiceURI(URI uri)
    {
        this.discoveryServiceURI = uri;
        return this;
    }
}
