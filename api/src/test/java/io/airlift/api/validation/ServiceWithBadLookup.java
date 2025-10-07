package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "bad lookup service", description = "blah blah")
public class ServiceWithBadLookup
{
    @ApiGet(description = "get bad lists")
    public Food getList(@ApiParameter BadLookupId id)
    {
        return null;
    }
}
