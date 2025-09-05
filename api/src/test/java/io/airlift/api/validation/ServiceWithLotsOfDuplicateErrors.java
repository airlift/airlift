package io.airlift.api.validation;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.ServiceType;

@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithLotsOfDuplicateErrors
{
    @ApiResource(name = "bad", description = "something")
    public record BadResource(@ApiDescription("description") String name, @ApiDescription("this is an illegal name") FoodId blahBlahBlah) {}

    @ApiCreate(description = "test", quotas = "dummy")
    public void createBad(BadResource badResource)
    {
        // NOP
    }

    @ApiCustom(type = ApiType.CREATE, verb = "test", description = "test", quotas = "dummy")
    public void createGood(BadResource badResource)
    {
        // NOP
    }
}
