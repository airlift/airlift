package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service", documentationLinks = "starburst-galaxy-link1-invalid-[]")
public class ServiceWithNoUriLinks
{
    @ApiGet(description = "get the new thing")
    public Thing getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }
}
