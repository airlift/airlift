package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithReadOnlyResources
{
    @ApiGet(description = "get the new thing")
    public ReadOnlyResource getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }
}
