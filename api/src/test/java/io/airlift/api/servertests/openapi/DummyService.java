package io.airlift.api.servertests.openapi;

import io.airlift.api.ApiGet;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;

@SuppressWarnings("unused")
@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class DummyService
{
    @ApiGet(description = "Get things")
    public DummyResource get()
    {
        return null;
    }
}
