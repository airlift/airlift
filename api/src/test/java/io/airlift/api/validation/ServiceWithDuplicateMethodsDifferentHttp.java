package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithDuplicateMethodsDifferentHttp
{
    @ApiGet(description = "get the new thing")
    public Thing getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }

    @ApiCreate(description = "get the new thing")
    public Thing alsoGetThing(@ApiParameter ThingId thingId)
    {
        return null;
    }
}
