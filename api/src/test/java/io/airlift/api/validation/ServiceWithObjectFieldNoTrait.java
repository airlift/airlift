package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "objectFieldServiceNoTrait", type = ServiceType.class, description = "Service with an Object field but no trait")
public class ServiceWithObjectFieldNoTrait
{
    @ApiCreate(description = "create something with an Object field", quotas = "yep")
    public void create(ResourceWithObjectField ignore) {}
}
