package com.proofpoint.discovery.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;
import java.net.URI;

public class ServiceInventoryConfig
{
    private URI serviceInventoryUri;

    public URI getServiceInventoryUri()
    {
        return serviceInventoryUri;
    }

    @Config("service-inventory.uri")
    @ConfigDescription("Service inventory base URI")
    public ServiceInventoryConfig setServiceInventoryUri(URI uri)
    {
        this.serviceInventoryUri = uri;
        return this;
    }
}
