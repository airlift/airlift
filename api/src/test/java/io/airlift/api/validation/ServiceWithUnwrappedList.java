package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "unwrappedListService", type = ServiceType.class, description = "Service with @ApiUnwrapped List field")
public class ServiceWithUnwrappedList
{
    @ApiCreate(description = "create something with unwrapped list")
    public void create(ResourceWithUnwrappedList ignore) {}
}
