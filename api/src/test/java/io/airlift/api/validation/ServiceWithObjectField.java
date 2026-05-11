package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;

@ApiService(name = "objectFieldService", type = ObjectFieldServiceType.class, description = "Service with an Object field")
public class ServiceWithObjectField
{
    @ApiCreate(description = "create something with an Object field")
    public void create(ResourceWithObjectField ignore) {}
}
