package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "objectService", type = ServiceType.class, description = "A service with object fields")
public class ServiceWithObject
{
    @ApiGet(description = "get the thing with object")
    public ResourceWithObject getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }
}
