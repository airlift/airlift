package io.airlift.api.validation;

import io.airlift.api.ApiDeprecated;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class Service
{
    @ApiGet(description = "get the new thing")
    public Thing getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }

    @ApiGet(description = "get the new thing")
    @ApiDeprecated(information = "It's dead Jim", deprecationDate = "2022-10-06", newImplementationClass = Service.class, newImplementationMethod = "getThing")
    public Thing oldGetThing()
    {
        return null;
    }
}
