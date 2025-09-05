package io.airlift.api.validation;

import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithNoVersion
{
    @ApiUpdate(description = "dummy")
    public void update(NoVersionResource bad)
    {
    }

    @ApiUpdate(description = "dummy")
    public void update2(@ApiParameter ThingId id, NoVersionResource bad)
    {
    }
}
