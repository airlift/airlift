package io.airlift.api.validation;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "acceptable list service", description = "blah blah")
public class ServiceWithAcceptableLists
{
    @ApiGet(description = "get acceptable lists")
    public AcceptableListResource getList(@ApiParameter AcceptableListResourceId id)
    {
        return null;
    }

    @ApiGet(description = "get optional collections")
    public OptionalCollections getOptionalCollections()
    {
        return null;
    }
}
