package com.proofpoint.experimental.discovery.client;

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

    @Config("server.discovery-uri")
    @ConfigDescription("Discovery service URI, including port")
    public DiscoveryClientConfig setDiscoveryServiceURI(URI uri)
    {
        this.discoveryServiceURI = uri;
        return this;
    }
}
