package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "objectListFieldServiceNoTrait", type = ServiceType.class, description = "Service with an Object list field but no trait")
public class ServiceWithObjectListFieldNoTrait
{
    @ApiCreate(description = "create something with an Object list field", quotas = "yep")
    public void create(ResourceWithObjectListField ignore) {}
}
