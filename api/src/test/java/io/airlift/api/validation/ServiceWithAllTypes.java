package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(type = ServiceType.class, name = "all types service", description = "all types")
public class ServiceWithAllTypes
{
    @ApiCreate(description = "all types", quotas = "yep")
    public void createAllTypes(ResourceWithAllTypes ignore)
    {
    }
}
