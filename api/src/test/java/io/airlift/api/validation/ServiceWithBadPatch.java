package io.airlift.api.validation;

import io.airlift.api.ApiPatch;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServiceWithBadPatch
{
    @ApiUpdate(description = "dummy")
    public void create(ApiPatch<String> noGood)
    {
    }
}
