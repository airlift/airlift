package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithRecursive
{
    @ApiCreate(description = "create recursive", quotas = "rec")
    public void createRecursive(RecursiveModel recursiveModel)
    {
    }
}
