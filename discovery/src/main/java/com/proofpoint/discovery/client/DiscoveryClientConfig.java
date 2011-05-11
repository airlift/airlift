package com.proofpoint.discovery.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;
import java.net.URI;

public class DiscoveryClientConfig
{
    private URI discoveryServiceURI;

    @NotNull
    public URI getDiscoveryServiceURI()
    {
        return discoveryServiceURI;
    }

    @Config("discovery.uri")
    @ConfigDescription("Discovery service base URI")
    public DiscoveryClientConfig setDiscoveryServiceURI(URI uri)
    {
        this.discoveryServiceURI = uri;
        return this;
    }
}
