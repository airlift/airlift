package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithPolyCollections
{
    @ApiGet(description = "get the thing")
    public PolyWithCollections getThing(@ApiParameter ThingId thingId)
    {
        return null;
    }

    @ApiCreate(description = "create the thing", quotas = "things")
    public void createThing(PolyWithCollections thing) {}
}
