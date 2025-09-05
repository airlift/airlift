package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithBadVersion
{
    @ApiCreate(description = "dummy")
    public void create(BadVersionResource bad)
    {
    }
}
