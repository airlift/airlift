package io.airlift.api.servertests.openapi.recursive;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiTrait;

@SuppressWarnings("unused")
@ApiService(type = SimpleServiceType.class, name = "simple", description = "Has simple recursive poly resources")
public class SimpleRecursiveService
{
    @ApiCreate(description = "dummy", traits = ApiTrait.BETA)
    public void create(SimpleRecursive simpleRecursive)
    {
        // NOP
    }
}
