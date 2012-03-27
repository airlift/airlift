package com.proofpoint.discovery.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ServiceInventoryConfig
{
    private URI serviceInventoryUri;
    private Duration updateRate = new Duration(10, TimeUnit.SECONDS);

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

    @NotNull
    public Duration getUpdateRate()
    {
        return updateRate;
    }

    @Config("service-inventory.update-interval")
    @ConfigDescription("Service inventory update interval")
    public ServiceInventoryConfig setUpdateRate(Duration updateRate)
    {
        this.updateRate = updateRate;
        return this;
    }
}
