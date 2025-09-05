package io.airlift.api.validation;

import io.airlift.api.ApiCustom;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(name = "service", type = ServiceType.class, description = "A service")
public class ServicePatchWithPatchAndPatch
{
    @ApiCustom(verb = "boom", type = ApiType.UPDATE, description = "dummy")
    public void create(ApiPatch<Thing> patch, ApiPatch<Thing> patchParameter)
    {
    }
}
