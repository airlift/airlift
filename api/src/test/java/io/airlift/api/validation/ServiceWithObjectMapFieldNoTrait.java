package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "objectMapFieldServiceNoTrait", type = ServiceType.class, description = "Service with an Object map field but no trait")
public class ServiceWithObjectMapFieldNoTrait
{
    @ApiCreate(description = "create something with an Object map field", quotas = "yep")
    public void create(ResourceWithObjectMapField ignore) {}
}
